package com.ratemyworkplace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PageViewInterceptor pageViewInterceptor;

    public WebConfig(PageViewInterceptor pageViewInterceptor) {
        this.pageViewInterceptor = pageViewInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pageViewInterceptor);
    }

    /**
     * CSS/JS/images are re-requested on every page navigation (this app has no
     * asset fingerprinting), so a short cache window lets the browser skip the
     * round-trip entirely within a session while still picking up deploys quickly.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**", "/js/**", "/img/**", "/assets/**")
                .addResourceLocations("classpath:/static/css/", "classpath:/static/js/",
                        "classpath:/static/img/", "classpath:/static/assets/")
                .setCacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic());
    }
}
