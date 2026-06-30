package com.ratemyworkplace.service;

import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.domain.VerificationToken;
import com.ratemyworkplace.repository.UserRepository;
import com.ratemyworkplace.repository.VerificationTokenRepository;
import com.ratemyworkplace.web.ApiException;
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

    @Transactional
    public void reset(String email, String code, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.badRequest("Invalid email or code"));
        VerificationToken token = tokenRepository
                .findFirstByUserIdAndChannelAndConsumedFalseOrderByIdDesc(
                        user.getId(), VerificationToken.Channel.PASSWORD_RESET)
                .orElseThrow(() -> ApiException.badRequest("No active reset request. Start over."));
        if (token.isExpired()) {
            throw ApiException.badRequest("That code has expired. Request a new one.");
        }
        if (!token.getCode().equals(code.trim())) {
            throw ApiException.badRequest("Incorrect code.");
        }
        token.setConsumed(true);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
