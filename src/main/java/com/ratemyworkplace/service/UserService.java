package com.ratemyworkplace.service;

import com.ratemyworkplace.domain.ModeratorPermission;
import com.ratemyworkplace.domain.Role;
import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.domain.VerificationToken;
import com.ratemyworkplace.dto.Requests;
import com.ratemyworkplace.repository.UserRepository;
import com.ratemyworkplace.security.SessionInvalidationService;
import com.ratemyworkplace.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;
    private final AnalyticsService analyticsService;
    private final FileStorageService fileStorageService;
    private final SessionInvalidationService sessionInvalidationService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       VerificationService verificationService, AnalyticsService analyticsService,
                       FileStorageService fileStorageService, SessionInvalidationService sessionInvalidationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationService = verificationService;
        this.analyticsService = analyticsService;
        this.fileStorageService = fileStorageService;
        this.sessionInvalidationService = sessionInvalidationService;
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
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setDisplayName(req.displayName().trim());
        user.setUsername(req.username().trim());
        user.setEmail(req.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        user = userRepository.save(user);

        verificationService.issue(user);
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
            verificationService.issue(user);
        }
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
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

    /** Stores a new profile picture, replacing (and deleting) any previous one. */
    @Transactional
    public User updateAvatar(User user, MultipartFile file) {
        String stored = fileStorageService.storeImage(file);
        String previous = user.getAvatarFileName();
        user.setAvatarFileName(stored);
        user.setAvatarContentType(file.getContentType());
        User saved = userRepository.save(user);
        if (previous != null && !previous.equals(stored)) {
            fileStorageService.delete(previous);
        }
        return saved;
    }

    /** Removes the current profile picture, if any. */
    @Transactional
    public User removeAvatar(User user) {
        String previous = user.getAvatarFileName();
        if (previous == null) {
            return user;
        }
        user.setAvatarFileName(null);
        user.setAvatarContentType(null);
        User saved = userRepository.save(user);
        fileStorageService.delete(previous);
        return saved;
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
        User saved = userRepository.save(user);

        sessionInvalidationService.invalidateSessionsFor(saved.getUsername());
        return saved;
    }

    private ModeratorPermission parsePermission(String raw) {
        try {
            return ModeratorPermission.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown moderator permission: " + raw);
        }
    }
}