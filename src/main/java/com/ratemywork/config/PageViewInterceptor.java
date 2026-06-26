package com.ratemywork.config;

import com.ratemywork.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Counts top-level HTML navigations toward the daily traffic graph. */
@Component
public class PageViewInterceptor implements HandlerInterceptor {

    private final AnalyticsService analyticsService;

    public PageViewInterceptor(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!"GET".equals(request.getMethod())) {
            return true;
        }
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        boolean htmlNavigation = accept != null && accept.contains("text/html")
                && (uri.equals("/") || uri.endsWith(".html"));
        if (htmlNavigation) {
            try {
                analyticsService.recordPageView();
            } catch (Exception ignored) {
                // Never let analytics break a page load.
            }
        }
        return true;
    }
}
