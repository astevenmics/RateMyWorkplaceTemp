package com.ratemywork.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-client-IP token-bucket rate limiter to blunt brute-force and DDoS-style
 * floods. Two buckets are kept per IP: a generous global budget and a much
 * stricter budget that applies to authentication and write endpoints.
 *
 * <p>Runs very early in the chain (before Spring Security) so abusive traffic is
 * shed before it can touch the database.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final ConcurrentMap<String, Bucket> globalBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> sensitiveBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        // Never throttle static assets or health checks.
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/")
                || path.startsWith("/assets/") || path.equals("/favicon.ico")
                || path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = clientIp(request);
        boolean sensitive = isSensitive(request);

        Bucket bucket = sensitive
                ? sensitiveBuckets.computeIfAbsent(ip, k -> newSensitiveBucket())
                : globalBuckets.computeIfAbsent(ip, k -> newGlobalBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
            response.setStatus(429); // Too Many Requests
            response.addHeader("Retry-After", Long.toString(waitSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Rate limit exceeded. Please slow down.\"}");
        }
    }

    private boolean isSensitive(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        boolean writeMethod = method.equals("POST") || method.equals("PUT")
                || method.equals("PATCH") || method.equals("DELETE");
        return writeMethod && path.startsWith("/api/");
    }

    private Bucket newGlobalBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(props.getCapacity(),
                        Refill.greedy(props.getRefillTokens(), Duration.ofSeconds(props.getRefillPeriodSeconds()))))
                .build();
    }

    private Bucket newSensitiveBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(props.getSensitiveCapacity(),
                        Refill.greedy(props.getSensitiveRefillTokens(),
                                Duration.ofSeconds(props.getSensitiveRefillPeriodSeconds()))))
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
