package com.myworktea.security;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import tools.jackson.databind.ObjectMapper;
import com.myworktea.config.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.time.Instant;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final RestAuthEntryPoint restAuthEntryPoint;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(RestAuthEntryPoint restAuthEntryPoint, RateLimitFilter rateLimitFilter) {
        this.restAuthEntryPoint = restAuthEntryPoint;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Tracks active sessions per principal so an admin action (disabling an account,
     * changing moderator permissions) can force that user's already-logged-in sessions
     * to re-authenticate, instead of the change being silently invisible until they
     * happen to log out and back in on their own.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Required so the session registry is told when a session is destroyed (browser logout, timeout, etc). */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // A generous cap: this only exists to enable registry tracking for
                        // forced invalidation, not to actually limit concurrent devices/browsers.
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(20)
                                .sessionRegistry(sessionRegistry)
                                .expiredSessionStrategy(this::writeExpiredSession)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "connect-src 'self'; " +
                                        "object-src 'none'; " +
                                        "frame-ancestors 'none'; " +
                                        "base-uri 'self'; " +
                                        "form-action 'self'"))
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy(
                                "geolocation=(), camera=(), microphone=(), payment=(), usb=()")))
                .authorizeHttpRequests(auth -> auth
                        // ---- public static pages & assets ----
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/img/**",
                                "/assets/**", "/favicon/**", "/*.html", "/error").permitAll()
                        // ---- public read-only API ----
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/companies/**", "/api/categories/**", "/api/locations/**",
                                "/api/feedback/location/**", "/api/feedback/company/**",
                                "/api/site/updates/**", "/api/stats/public", "/api/users/*/avatar").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/csrf",
                                "/api/auth/me", "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/api/auth/reactivate", "/api/site/feedback").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // ---- admin / moderation ----
                        .requestMatchers("/admin.html").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/mod/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // ---- everything else requires login ----
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler((req, res, authentication) -> writeJson(res, HttpServletResponse.SC_OK,
                                Map.of("status", 200, "message", "Logged in", "username", authentication.getName())))
                        .failureHandler((req, res, ex) -> writeJson(res, HttpServletResponse.SC_UNAUTHORIZED,
                                Map.of("status", 401, "error", "Unauthorized", "message", "Invalid credentials"))))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, authentication) -> writeJson(res, HttpServletResponse.SC_OK,
                                Map.of("status", 200, "message", "Logged out")))
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthEntryPoint))
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    private void writeExpiredSession(
            org.springframework.security.web.session.SessionInformationExpiredEvent event) throws java.io.IOException {
        jakarta.servlet.http.HttpServletRequest request = event.getRequest();
        HttpServletResponse response = event.getResponse();
        if (isBrowserNavigation(request)) {
            response.sendRedirect(request.getContextPath() + "/index.html?sessionExpired=1");
            return;
        }
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Map.of(
                "status", 401, "error", "Unauthorized", "code", "SESSION_INVALIDATED",
                "message", "Your session has ended because your account access changed. Please log in again."));
    }

    private boolean isBrowserNavigation(jakarta.servlet.http.HttpServletRequest request) {
        String fetchMode = request.getHeader("Sec-Fetch-Mode");
        if (fetchMode != null) {
            return "navigate".equals(fetchMode);
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private void writeJson(HttpServletResponse response, int status, Map<String, Object> body) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>(body);
            payload.putIfAbsent("timestamp", Instant.now().toString());
            objectMapper.writeValue(response.getOutputStream(), payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}