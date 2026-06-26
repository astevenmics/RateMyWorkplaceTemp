package com.ratemywork.service;

import com.ratemywork.domain.User;
import com.ratemywork.domain.VerificationToken;
import com.ratemywork.repository.UserRepository;
import com.ratemywork.repository.VerificationTokenRepository;
import com.ratemywork.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Issues and validates email/phone verification codes. */
@Service
public class VerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long EXPIRY_MINUTES = 30;

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public VerificationService(VerificationTokenRepository tokenRepository,
                               UserRepository userRepository,
                               NotificationService notificationService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void issue(User user, VerificationToken.Channel channel) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setChannel(channel);
        token.setCode(code);
        token.setExpiresAt(Instant.now().plus(EXPIRY_MINUTES, ChronoUnit.MINUTES));
        tokenRepository.save(token);

        if (channel == VerificationToken.Channel.EMAIL) {
            notificationService.sendEmailCode(user.getEmail(), code);
        } else {
            notificationService.sendPhoneCode(user.getPhoneNumber(), code);
        }
    }

    @Transactional
    public void verify(User user, VerificationToken.Channel channel, String code) {
        VerificationToken token = tokenRepository
                .findFirstByUserIdAndChannelAndConsumedFalseOrderByIdDesc(user.getId(), channel)
                .orElseThrow(() -> ApiException.badRequest("No active code. Request a new one."));

        if (token.isExpired()) {
            throw ApiException.badRequest("That code has expired. Request a new one.");
        }
        if (!token.getCode().equals(code.trim())) {
            throw ApiException.badRequest("Incorrect code.");
        }

        token.setConsumed(true);
        if (channel == VerificationToken.Channel.EMAIL) {
            user.setEmailVerified(true);
        } else {
            user.setPhoneVerified(true);
        }
        userRepository.save(user);
    }
}
