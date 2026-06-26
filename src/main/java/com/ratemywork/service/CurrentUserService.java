package com.ratemywork.service;

import com.ratemywork.domain.User;
import com.ratemywork.repository.UserRepository;
import com.ratemywork.security.AppUserDetails;
import com.ratemywork.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Resolves the {@link User} backing the current security context. */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppUserDetails details)) {
            return Optional.empty();
        }
        return userRepository.findById(details.getId());
    }

    public User require() {
        return current().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "You must be logged in"));
    }

    public User requireVerified() {
        User user = require();
        if (!user.isFullyVerified()) {
            throw ApiException.forbidden("Verify your email and phone number before continuing");
        }
        return user;
    }
}
