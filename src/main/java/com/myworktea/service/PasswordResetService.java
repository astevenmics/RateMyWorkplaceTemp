package com.myworktea.service;

import com.myworktea.domain.User;
import com.myworktea.domain.VerificationToken;
import com.myworktea.repository.UserRepository;
import com.myworktea.repository.VerificationTokenRepository;
import com.myworktea.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Handles the "forgot password" flow using a short-lived emailed reset code. */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long EXPIRY_MINUTES = 30;
    private static final int MAX_ATTEMPTS = 5;
    /** Single generic message for every failure mode, so the response can't be used to enumerate accounts. */
    private static final String INVALID_MESSAGE = "Invalid or expired reset code. Request a new one.";

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public PasswordResetService(UserRepository userRepository, VerificationTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    /** Emails a reset code if the address belongs to an account. Never reveals whether it does. */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            VerificationToken token = new VerificationToken();
            token.setUser(user);
            token.setChannel(VerificationToken.Channel.PASSWORD_RESET);
            token.setCode(code);
            token.setExpiresAt(Instant.now().plus(EXPIRY_MINUTES, ChronoUnit.MINUTES));
            tokenRepository.save(token);
            notificationService.notifyPasswordReset(user.getEmail(), user.getDisplayName(), code);
        });
    }

    // noRollbackFor is required: without it, the attempts/consumed write below would be undone by Spring's default
    // rollback-on-RuntimeException before ApiException leaves this method, silently defeating the attempt cap.
    @Transactional(noRollbackFor = ApiException.class)
    public void reset(String email, String code, String newPassword) {
        // Every failure below throws the same message/status
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.badRequest(INVALID_MESSAGE));
        VerificationToken token = tokenRepository
                .findFirstByUserIdAndChannelAndConsumedFalseOrderByIdDesc(
                        user.getId(), VerificationToken.Channel.PASSWORD_RESET)
                .orElseThrow(() -> ApiException.badRequest(INVALID_MESSAGE));
        if (token.isExpired()) {
            throw ApiException.badRequest(INVALID_MESSAGE);
        }
        if (!token.getCode().equals(code.trim())) {
            token.setAttempts(token.getAttempts() + 1);
            if (token.getAttempts() >= MAX_ATTEMPTS) {
                token.setConsumed(true);
            }
            tokenRepository.save(token);
            throw ApiException.badRequest(INVALID_MESSAGE);
        }
        token.setConsumed(true);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}