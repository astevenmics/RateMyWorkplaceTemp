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

    // ----- account lifecycle -----
    @Async
    public void notifyAccountDisabled(String to, String displayName) {
        send(to, "Your RateMyWorkplace account has been disabled",
                greeting(displayName) + "Your account has been disabled by an administrator, so you can no "
                        + "longer sign in. If you believe this is a mistake, reply to this email or contact support.");
    }

    @Async
    public void notifyAccountEnabled(String to, String displayName) {
        send(to, "Your RateMyWorkplace account has been re-enabled",
                greeting(displayName) + "Good news — your account has been re-enabled and you can sign in again.");
    }

    @Async
    public void notifyAccountDeleted(String to, String displayName) {
        send(to, "Your RateMyWorkplace account has been removed",
                greeting(displayName) + "Your account and its content have been removed from RateMyWorkplace. "
                        + "If you believe this was a mistake, please contact support.");
    }

    // ----- feedback -----
    @Async
    public void notifyFeedbackRemoved(String to, String displayName, String companyName) {
        send(to, "Your feedback was removed",
                greeting(displayName) + "Your feedback for \"" + companyName + "\" has been removed by a moderator, "
                        + "typically due to a Terms & Conditions violation. You're welcome to post new feedback that "
                        + "follows our guidelines.");
    }

    // ----- employment proof review -----
    @Async
    public void notifyProofReviewed(String to, String displayName, String companyName, String locationLabel,
                                    boolean approved, String note) {
        String scope = (locationLabel == null || locationLabel.isBlank())
                ? companyName + " (company-wide)" : companyName + " — " + locationLabel;
        String subject = approved
                ? "Your employment verification was approved"
                : "Your employment verification was rejected";
        StringBuilder body = new StringBuilder(greeting(displayName));
        if (approved) {
            body.append("Your employment proof for ").append(scope)
                    .append(" has been approved. You can now post feedback for this location.");
        } else {
            body.append("Your employment proof for ").append(scope).append(" was rejected.");
        }
        appendNote(body, note);
        send(to, subject, body.toString());
    }

    // ----- workplace submission review -----
    @Async
    public void notifyWorkplaceReviewed(String to, String displayName, String companyName,
                                        boolean approved, String note) {
        String subject = approved
                ? "Your workplace submission was approved"
                : "Your workplace submission was rejected";
        StringBuilder body = new StringBuilder(greeting(displayName));
        if (approved) {
            body.append("The workplace you submitted, \"").append(companyName)
                    .append("\", has been approved and is now publicly listed. Thank you for contributing!");
        } else {
            body.append("The workplace you submitted, \"").append(companyName).append("\", was not approved.");
        }
        appendNote(body, note);
        send(to, subject, body.toString());
    }

    private static String greeting(String displayName) {
        return "Hi " + (displayName == null || displayName.isBlank() ? "there" : displayName) + ",\n\n";
    }

    private static void appendNote(StringBuilder body, String note) {
        if (note != null && !note.isBlank()) {
            body.append("\n\nNote from the reviewer: ").append(note);
        }
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
