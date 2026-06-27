package com.ratemyworkplace.security;

import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.repository.UserRepository;
import com.ratemyworkplace.service.AnalyticsService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Records a login and stamps {@code lastLoginAt} whenever a user authenticates. */
@Component
public class LoginEventListener {

    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    public LoginEventListener(UserRepository userRepository, AnalyticsService analyticsService) {
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
    }

    @EventListener
    @Transactional
    public void onSuccess(InteractiveAuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof AppUserDetails details) {
            userRepository.findById(details.getId()).ifPresent(this::stamp);
            analyticsService.recordLogin();
        }
    }

    private void stamp(User user) {
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }
}
