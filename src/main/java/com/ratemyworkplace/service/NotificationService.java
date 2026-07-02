package com.ratemyworkplace.service;

import com.ratemyworkplace.config.AppMailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends verification codes and notices. When {@code app.mail.enabled=false}
 * (the default for local development) messages are logged instead of e-mailed,
 * so the platform is fully testable without an SMTP server. SMS for phone
 * verification is logged the same way (wire a real gateway in production).
 *
 * <p>Sends run on a background executor ({@code @Async}) so a slow SMTP server
 * never blocks the request thread for register / profile-update / resend.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final AppMailProperties mailProperties;
    private final JavaMailSender mailSender;

    public NotificationService(AppMailProperties mailProperties, JavaMailSender mailSender) {
        this.mailProperties = mailProperties;
        this.mailSender = mailSender;
    }

    @Async
    public void sendEmailCode(String email, String code) {
        String subject = "Your RateMyWorkplace verification code";
        String text = "Welcome to RateMyWorkplace!\n\nYour email verification code is: " + code
                + "\n\nThis code expires in 30 minutes.";
        send(email, subject, text);
    }

    @Async
    public void sendPhoneCode(String phoneNumber, String code) {
        // No SMS gateway configured by default; log so codes are usable in dev.
        log.info("[SMS->{}] RateMyWorkplace verification code: {}", phoneNumber, code);
    }

    @Async
    public void send(String to, String subject, String text) {
        if (!mailProperties.isEnabled()) {
            log.info("[EMAIL DISABLED] to={} subject='{}'\n{}", to, subject, text);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }
}