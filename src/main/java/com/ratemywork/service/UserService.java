package com.ratemywork.service;

import com.ratemywork.domain.ModeratorPermission;
import com.ratemywork.domain.Role;
import com.ratemywork.domain.User;
import com.ratemywork.domain.VerificationToken;
import com.ratemywork.dto.Requests;
import com.ratemywork.repository.UserRepository;
import com.ratemywork.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;
    private final AnalyticsService analyticsService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       VerificationService verificationService, AnalyticsService analyticsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationService = verificationService;
        this.analyticsService = analyticsService;
    }

    @Transactional
    public User register(Requests.RegisterRequest req) {
        if (userRepository.existsByUsernameIgnoreCase(req.username())) {
            throw ApiException.conflict("That username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("An account with that email already exists");
        }

        User user = new User();
        user.setDisplayName(req.displayName().trim());
        user.setUsername(req.username().trim());
        user.setEmail(req.email().trim().toLowerCase());
        user.setPhoneNumber(req.phoneNumber().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        user = userRepository.save(user);

        verificationService.issue(user, VerificationToken.Channel.EMAIL);
        verificationService.issue(user, VerificationToken.Channel.PHONE);
        analyticsService.recordSignup();
        return user;
    }

    @Transactional
    public User updateProfile(User user, Requests.UpdateProfileRequest req) {
        String newEmail = req.email().trim().toLowerCase();
        if (!newEmail.equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailIgnoreCase(newEmail)) {
                throw ApiException.conflict("An account with that email already exists");
            }
            user.setEmail(newEmail);
            user.setEmailVerified(false);
            verificationService.issue(user, VerificationToken.Channel.EMAIL);
        }
        if (!req.phoneNumber().trim().equals(user.getPhoneNumber())) {
            user.setPhoneNumber(req.phoneNumber().trim());
            user.setPhoneVerified(false);
            verificationService.issue(user, VerificationToken.Channel.PHONE);
        }
        user.setDisplayName(req.displayName().trim());

        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            if (req.currentPassword() == null
                    || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                throw ApiException.badRequest("Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        }
        return userRepository.save(user);
    }

    @Transactional
    public User setModeratorPermissions(Requests.ModeratorRequest req) {
        User user = userRepository.findByUsernameIgnoreCase(req.username().trim())
                .orElseThrow(() -> ApiException.notFound("No user with username '" + req.username() + "'"));

        Set<ModeratorPermission> permissions = req.permissions().stream()
                .map(p -> parsePermission(p))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ModeratorPermission.class)));

        user.setModeratorPermissions(permissions);
        if (user.getRole() != Role.ADMIN) {
            user.setRole(permissions.isEmpty() ? Role.USER : Role.MODERATOR);
        }
        return userRepository.save(user);
    }

    private ModeratorPermission parsePermission(String raw) {
        try {
            return ModeratorPermission.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown moderator permission: " + raw);
        }
    }
}
