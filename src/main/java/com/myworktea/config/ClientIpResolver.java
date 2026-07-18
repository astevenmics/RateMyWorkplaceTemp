package com.myworktea.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the caller's IP address, honoring {@code app.ratelimit.trust-forwarded-for} the same
 * way everywhere it matters (rate limiting, anonymous rant vote/post dedup) so those don't quietly
 * disagree about which proxy headers are trustworthy.
 */
@Component
public class ClientIpResolver {

    private final RateLimitProperties props;

    public ClientIpResolver(RateLimitProperties props) {
        this.props = props;
    }

    public String resolve(HttpServletRequest request) {
        if (props.isTrustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        return request.getRemoteAddr();
    }
}
