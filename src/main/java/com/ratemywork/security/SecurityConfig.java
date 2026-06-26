package com.ratemywork.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratemywork.config.RateLimitFilter;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                    // ---- public static pages & assets ----
                    .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/img/**",
                            "/assets/**", "/favicon.ico", "/*.html", "/error").permitAll()
                    // ---- public read-only API ----
                    .requestMatchers(org.springframework.http.HttpMethod.GET,
                            "/api/companies/**", "/api/categories/**", "/api/locations/**",
                            "/api/feedback/location/**", "/api/feedback/company/**",
                            "/api/site/updates/**", "/api/stats/public").permitAll()
                    .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/csrf",
                            "/api/auth/me", "/api/site/feedback").permitAll()
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
