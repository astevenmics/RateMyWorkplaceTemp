package com.ratemyworkplace.service;

import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.domain.VerificationToken;
import com.ratemyworkplace.repository.UserRepository;
import com.ratemyworkplace.repository.VerificationTokenRepository;
import com.ratemyworkplace.web.ApiException;
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

    /** Minimum interval between resend requests for the same channel. */
    private static final long RESEND_COOLDOWN_MINUTES = 5;

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

    /**
     * Re-issues a verification code, but only if the channel isn't already verified and the
     * last code was sent more than {@value #RESEND_COOLDOWN_MINUTES} minutes ago.
     */
    @Transactional
    public void resend(User user, VerificationToken.Channel channel) {
        boolean alreadyVerified = channel == VerificationToken.Channel.EMAIL
                ? user.isEmailVerified() : user.isPhoneVerified();
        if (alreadyVerified) {
            throw ApiException.badRequest("That contact is already verified.");
        }
        // A token has a 30-minute lifetime; if the latest one still has > 25 minutes left,
        // it was issued within the last 5 minutes, so enforce the cooldown.
        tokenRepository.findFirstByUserIdAndChannelAndConsumedFalseOrderByIdDesc(user.getId(), channel)
                .ifPresent(latest -> {
                    Instant issuedFloor = Instant.now()
                            .plus(EXPIRY_MINUTES - RESEND_COOLDOWN_MINUTES, ChronoUnit.MINUTES);
                    if (latest.getExpiresAt().isAfter(issuedFloor)) {
                        long secs = java.time.Duration.between(issuedFloor, latest.getExpiresAt()).getSeconds();
                        throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                                "Please wait about " + Math.max(1, (secs + 59) / 60)
                                        + " more minute(s) before requesting another code.");
                    }
                });
        issue(user, channel);
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
