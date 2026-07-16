package com.myworktea.service;

import com.myworktea.domain.ModeratorPermission;
import com.myworktea.domain.Role;
import com.myworktea.domain.User;
import com.myworktea.dto.Requests;
import com.myworktea.repository.UserRepository;
import com.myworktea.security.SessionInvalidationService;
import com.myworktea.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final NotificationService notificationService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       VerificationService verificationService, AnalyticsService analyticsService,
                       FileStorageService fileStorageService, SessionInvalidationService sessionInvalidationService,
                       NotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationService = verificationService;
        this.analyticsService = analyticsService;
        this.fileStorageService = fileStorageService;
        this.sessionInvalidationService = sessionInvalidationService;
        this.notificationService = notificationService;
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
                .map(this::parsePermission)
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

    // ---- self-service account disable / delete ----

    /** Deactivates the caller's own account: they can no longer sign in, but their feedback and posts stay visible. */
    @Transactional
    public void disableAccount(User user, String password) {
        verifyPasswordForAccountAction(user, password);
        user.setEnabled(false);
        user.setSelfServiceDisabled(true);
        userRepository.save(user);
        sessionInvalidationService.invalidateSessionsFor(user.getUsername());
        notificationService.notifySelfDisabled(user.getEmail(), user.getDisplayName());
    }

    /**
     * Marks the caller's own account for deletion: it's disabled immediately, and permanently
     * purged (feedback, employment proofs, etc. — workplaces they submitted are kept, just
     * unlinked from the account) once {@link User#DELETION_GRACE_DAYS} have elapsed.
     */
    @Transactional
    public void requestAccountDeletion(User user, String password) {
        verifyPasswordForAccountAction(user, password);
        user.setEnabled(false);
        user.setSelfServiceDisabled(true);
        user.setDeletionRequestedAt(Instant.now());
        User saved = userRepository.save(user);
        sessionInvalidationService.invalidateSessionsFor(saved.getUsername());
        Instant purgeAt = saved.getDeletionRequestedAt().plus(User.DELETION_GRACE_DAYS, ChronoUnit.DAYS);
        notificationService.notifyDeletionScheduled(saved.getEmail(), saved.getDisplayName(), purgeAt);
    }

    @Transactional
    public void reactivateAccount(String usernameOrEmail, String password) {
        User user = userRepository.findByUsernameIgnoreCase(usernameOrEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(usernameOrEmail))
                .orElseThrow(() -> ApiException.badRequest("Incorrect username/email or password"));
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("Incorrect username/email or password");
        }
        if (user.isEnabled()) {
            throw ApiException.badRequest("This account is already active — just log in.");
        }
        if (!user.isSelfServiceDisabled()) {
            throw ApiException.badRequest(
                    "This account was disabled by an admin and can't be reactivated here. Please contact support.");
        }
        user.setEnabled(true);
        user.setSelfServiceDisabled(false);
        user.setDeletionRequestedAt(null);
        userRepository.save(user);
        notificationService.notifySelfReactivated(user.getEmail(), user.getDisplayName());
    }

    private void verifyPasswordForAccountAction(User user, String password) {
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.badRequest(
                    "Admin accounts can't be disabled or deleted this way — transfer admin duties first");
        }
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("Incorrect password");
        }
    }
}