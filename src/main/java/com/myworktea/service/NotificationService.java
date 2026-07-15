package com.myworktea.service;

import com.myworktea.config.AppMailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Sends transactional emails (verification codes, account &amp; moderation notices).
 * When {@code app.mail.enabled=false} (the local-dev default) messages are logged
 * instead of sent, so the platform is fully testable without an SMTP server.
 *
 * <p>All sends run on a background executor ({@code @Async}) so a slow SMTP server
 * never blocks the request thread.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String SIGNOFF =
            """
                    
                    
                    Best regards,
                    The MyWorkTea Team
                    
                    —
                    This is an automated message from MyWorkTea. Please do not reply to this email.""";

    private final AppMailProperties mailProperties;
    private final JavaMailSender mailSender;

    public NotificationService(AppMailProperties mailProperties, JavaMailSender mailSender) {
        this.mailProperties = mailProperties;
        this.mailSender = mailSender;
    }

    // ----- verification -----
    @Async
    public void sendEmailCode(String email, String code) {
        String body = "Welcome to MyWorkTea.\n\n"
                + "To finish verifying your email address, please enter the following code:\n\n"
                + "    " + code + "\n\n"
                + "For your security, this code will expire in 30 minutes. "
                + "If you did not create an account, you can safely ignore this email.";
        send(email, "Verify your email address", greeting(null) + body + SIGNOFF);
    }

    @Async
    public void notifyPasswordReset(String email, String displayName, String code) {
        String body = "We received a request to reset the password for your MyWorkTea account.\n\n"
                + "Please enter the following code to choose a new password:\n\n"
                + "    " + code + "\n\n"
                + "This code will expire in 30 minutes. If you did not request a password reset, "
                + "no action is needed — your password will remain unchanged.";
        send(email, "Reset your MyWorkTea password", greeting(displayName) + body + SIGNOFF);
    }

    // ----- account lifecycle -----
    @Async
    public void notifyAccountDisabled(String to, String displayName) {
        String body = """
                We are writing to let you know that your MyWorkTea account has been disabled by our \
                team, and you will not be able to sign in until it is reinstated.
                
                If you believe this was done in error, please contact our support team and we will be happy to help.""";
        send(to, "Your MyWorkTea account has been disabled", greeting(displayName) + body + SIGNOFF);
    }

    @Async
    public void notifyAccountEnabled(String to, String displayName) {
        String body = """
                Good news — your MyWorkTea account has been reinstated and you can now sign in again.
                
                Thank you for your patience.""";
        send(to, "Your MyWorkTea account has been reinstated", greeting(displayName) + body + SIGNOFF);
    }

    @Async
    public void notifyAccountDeleted(String to, String displayName) {
        String body = """
                We are writing to confirm that your MyWorkTea account, along with its associated \
                content, has been permanently removed.

                If you believe this was done in error, please contact our support team.""";
        send(to, "Your MyWorkTea account has been removed", greeting(displayName) + body + SIGNOFF);
    }

    @Async
    public void notifySelfDisabled(String to, String displayName) {
        String body = """
                This confirms that you've disabled your MyWorkTea account. You won't be able to sign \
                in while it's disabled, but your existing feedback and posts remain visible to others.

                Changed your mind? You can reactivate it any time from the Reactivate Account page — \
                just enter your username or email and your password.

                If this wasn't you, please contact our support team right away.""";
        send(to, "Your MyWorkTea account has been disabled", greeting(displayName) + body + SIGNOFF);
    }

    @Async
    public void notifyDeletionScheduled(String to, String displayName, Instant purgeAt) {
        String purgeDate = DateTimeFormatter.ofPattern("MMMM d, yyyy").withZone(ZoneOffset.UTC).format(purgeAt);
        String body = """
                This confirms that you've requested deletion of your MyWorkTea account. Your account \
                has been disabled immediately, and on %s it — along with your feedback, reviews and \
                employment proofs — will be permanently deleted. Workplaces you submitted will stay \
                listed, no longer credited to your account.

                Changed your mind? You can cancel this and reactivate your account any time before \
                then from the Reactivate Account page — just enter your username or email and your \
                password.

                If this wasn't you, please contact our support team right away.""".formatted(purgeDate);
        send(to, "Your MyWorkTea account is scheduled for deletion", greeting(displayName) + body + SIGNOFF);
    }

    @Async
    public void notifySelfReactivated(String to, String displayName) {
        String body = """
                This confirms that your MyWorkTea account has been reactivated and you can sign in \
                again. If a deletion was scheduled, it has been cancelled.

                If this wasn't you, please contact our support team right away.""";
        send(to, "Your MyWorkTea account has been reactivated", greeting(displayName) + body + SIGNOFF);
    }

    // ----- feedback -----
    @Async
    public void notifyFeedbackRemoved(String to, String displayName, String companyName) {
        String body = "We are writing to let you know that your review of \"" + companyName + "\" has been removed "
                + "by our moderation team, as it was found to be inconsistent with our Terms & Conditions and "
                + "community guidelines.\n\n"
                + "You are welcome to submit a new review that follows our guidelines at any time.";
        send(to, "Your review has been removed", greeting(displayName) + body + SIGNOFF);
    }

    // ----- employment proof review -----
    @Async
    public void notifyProofReviewed(String to, String displayName, String companyName, String locationLabel,
                                    boolean approved, String note) {
        String scope = (locationLabel == null || locationLabel.isBlank())
                ? companyName + " (all locations)" : companyName + " — " + locationLabel;
        String subject;
        StringBuilder body = new StringBuilder(greeting(displayName));
        if (approved) {
            subject = "Your employment verification has been approved";
            body.append("Good news — your employment verification for ").append(scope)
                    .append(" has been approved. You can now share feedback about this workplace.");
        } else {
            subject = "Update on your employment verification";
            body.append("Thank you for submitting an employment verification for ").append(scope)
                    .append(". After review, we were unable to approve it at this time.");
        }
        appendNote(body, note);
        send(to, subject, body.append(SIGNOFF).toString());
    }

    // ----- workplace submission review -----
    @Async
    public void notifyWorkplaceReviewed(String to, String displayName, String companyName,
                                        boolean approved, String note) {
        String subject;
        StringBuilder body = new StringBuilder(greeting(displayName));
        if (approved) {
            subject = "Your workplace submission has been approved";
            body.append("Thank you for contributing to MyWorkTea. The workplace you submitted, \"")
                    .append(companyName).append("\", has been approved and is now publicly listed.");
        } else {
            subject = "Update on your workplace submission";
            body.append("Thank you for submitting \"").append(companyName)
                    .append("\" to MyWorkTea. After review, we were unable to approve it at this time.");
        }
        appendNote(body, note);
        send(to, subject, body.append(SIGNOFF).toString());
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

    private static String greeting(String displayName) {
        return "Dear " + (displayName == null || displayName.isBlank() ? "Member" : displayName) + ",\n\n";
    }

    private static void appendNote(StringBuilder body, String note) {
        if (note != null && !note.isBlank()) {
            body.append("\n\nNote from the reviewer: ").append(note);
        }
    }
}