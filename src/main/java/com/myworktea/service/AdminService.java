package com.myworktea.service;

import com.myworktea.domain.*;
import com.myworktea.dto.Responses;
import com.myworktea.repository.*;
import com.myworktea.security.SessionInvalidationService;
import com.myworktea.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Admin/moderator operations: workplace approval, user management, deletions and statistics. */
@Service
public class AdminService {

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final FeedbackRepository feedbackRepository;
    private final EmploymentProofRepository proofRepository;
    private final UserRepository userRepository;
    private final SiteFeedbackRepository siteFeedbackRepository;
    private final SiteUpdateRepository siteUpdateRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final VisitLogRepository visitLogRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SessionInvalidationService sessionInvalidationService;

    public AdminService(CompanyRepository companyRepository, LocationRepository locationRepository,
                        FeedbackRepository feedbackRepository, EmploymentProofRepository proofRepository,
                        UserRepository userRepository, SiteFeedbackRepository siteFeedbackRepository,
                        SiteUpdateRepository siteUpdateRepository,
                        VerificationTokenRepository verificationTokenRepository,
                        VisitLogRepository visitLogRepository,
                        NotificationService notificationService,
                        AuditService auditService,
                        SessionInvalidationService sessionInvalidationService) {
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.feedbackRepository = feedbackRepository;
        this.proofRepository = proofRepository;
        this.userRepository = userRepository;
        this.siteFeedbackRepository = siteFeedbackRepository;
        this.siteUpdateRepository = siteUpdateRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.visitLogRepository = visitLogRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.sessionInvalidationService = sessionInvalidationService;
    }

    // ---- workplace approval ----
    public Page<Company> pendingCompanies(Pageable pageable) {
        return companyRepository.findByStatus(ApprovalStatus.PENDING, pageable);
    }

    @Transactional
    public Company reviewCompany(Long id, boolean approve) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Workplace not found"));
        company.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        Company saved = companyRepository.save(company);

        User submitter = saved.getSubmittedBy();
        if (submitter != null) {
            notificationService.notifyWorkplaceReviewed(submitter.getEmail(), submitter.getDisplayName(),
                    saved.getName(), approve, null);
        }
        String submittedBy = submitter != null ? submitter.getDisplayName() + " (@" + submitter.getUsername() + ")" : "unknown";
        auditService.record(AuditCategory.WORKPLACE, approve ? AuditAction.APPROVED : AuditAction.REJECTED,
                "Workplace '" + saved.getName() + "' " + (approve ? "approved" : "rejected"),
                "Submitted by " + submittedBy
                        + (saved.getWebsite() != null ? "\nWebsite: " + saved.getWebsite() : "")
                        + (saved.getDescription() != null ? "\n" + saved.getDescription() : ""),
                saved.getId());
        return saved;
    }

    @Transactional
    public void deleteCompany(Long id) {
        if (!companyRepository.existsById(id)) {
            throw ApiException.notFound("Workplace not found");
        }
        // Remove dependent rows first to satisfy FK constraints; locations cascade with the company.
        feedbackRepository.deleteByCompanyId(id);
        proofRepository.deleteByCompanyId(id);
        companyRepository.deleteById(id);
    }

    // ---- user management ----
    public Page<User> searchUsers(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return userRepository.findAll(pageable);
        }
        return userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                query.trim(), query.trim(), pageable);
    }

    @Transactional
    public User setEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getRole() == Role.ADMIN && !enabled) {
            throw ApiException.badRequest("You cannot disable an admin account");
        }
        boolean wasEnabled = user.isEnabled();
        user.setEnabled(enabled);
        user.setSelfServiceDisabled(false);
        if (enabled) {
            user.setDeletionRequestedAt(null);
        }
        User saved = userRepository.save(user);
        if (wasEnabled != enabled) {
            if (enabled) {
                notificationService.notifyAccountEnabled(saved.getEmail(), saved.getDisplayName());
            } else {
                notificationService.notifyAccountDisabled(saved.getEmail(), saved.getDisplayName());
                sessionInvalidationService.invalidateSessionsFor(saved.getUsername());
            }
        }
        return saved;
    }

    @Transactional
    public User flagUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setFlaggedReason(reason == null || reason.isBlank() ? null : reason.trim());
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("Admin accounts cannot be deleted from the panel");
        }
        purgeUser(user, "deleted by an admin");
    }

    /**
     * Nightly sweep: permanently purges self-service deletion requests whose
     * {@link User#DELETION_GRACE_DAYS}-day grace period has elapsed.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredDeletions() {
        Instant cutoff = Instant.now().minus(User.DELETION_GRACE_DAYS, ChronoUnit.DAYS);
        for (User user : userRepository.findByDeletionRequestedAtNotNullAndDeletionRequestedAtBefore(cutoff)) {
            purgeUser(user, "scheduled purge (self-requested account deletion)");
        }
    }

    /**
     * Deletes a user and everything that belongs only to them (feedback, employment proofs, verification tokens),
     * while detaching them (rather than deleting) from records that should survive them —
     * site feedback/updates they authored, and any workplace they submitted.
     */
    private void purgeUser(User user, String auditContext) {
        Long userId = user.getId();
        // Capture details before the account is removed, to notify them and keep a record.
        String email = user.getEmail();
        String displayName = user.getDisplayName();
        String snapshot = "Name: " + user.getFullName()
                + "\nDisplay name: " + displayName
                + "\nUsername: @" + user.getUsername()
                + "\nEmail: " + email
                + "\nRole: " + user.getRole()
                + "\nVerified: email=" + user.isEmailVerified()
                + (user.getFlaggedReason() != null ? "\nFlagged: " + user.getFlaggedReason() : "")
                + "\nJoined: " + user.getCreatedAt();
        // Clear or remove everything that references the user before deleting it.
        feedbackRepository.deleteByAuthorId(userId);
        proofRepository.deleteByUserId(userId);
        proofRepository.detachReviewer(userId);
        verificationTokenRepository.deleteByUserId(userId);
        siteFeedbackRepository.detachAuthor(userId);
        siteUpdateRepository.detachAuthor(userId);
        companyRepository.detachSubmitter(userId);
        userRepository.delete(user);

        notificationService.notifyAccountDeleted(email, displayName);
        auditService.record(AuditCategory.USER, AuditAction.DELETED,
                "User @" + user.getUsername() + " (" + displayName + ") deleted", snapshot + "\n\n" + auditContext, userId);
    }

    // ---- statistics ----
    public Responses.StatsDto stats() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        long totalUsers = userRepository.count();
        long verifiedUsers = userRepository.countByEmailVerifiedTrue();
        long admins = userRepository.countByRole(Role.ADMIN);
        long moderators = userRepository.countByRole(Role.MODERATOR);
        long newUsers = userRepository.countByCreatedAtAfter(thirtyDaysAgo);

        long totalCompanies = companyRepository.count();
        long approvedCompanies = companyRepository.countByStatus(ApprovalStatus.APPROVED);
        long pendingCompanies = companyRepository.countByStatus(ApprovalStatus.PENDING);
        long totalLocations = locationRepository.count();
        long totalFeedback = feedbackRepository.count();
        long pendingProofs = proofRepository.countByStatus(ApprovalStatus.PENDING);
        long pendingFeedback = feedbackRepository.countByStatus(ApprovalStatus.REJECTED);
        long openSiteFeedback = siteFeedbackRepository.countByResolved(false);

        LocalDate today = LocalDate.now();
        List<VisitLog> logs = visitLogRepository.findByDayBetweenOrderByDayAsc(today.minusDays(29), today);
        Map<LocalDate, VisitLog> byDay = logs.stream()
                .collect(Collectors.toMap(VisitLog::getDay, l -> l));
        List<Responses.DailyPoint> traffic = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            VisitLog log = byDay.get(day);
            traffic.add(new Responses.DailyPoint(day.toString(),
                    log != null ? log.getPageViews() : 0,
                    log != null ? log.getLogins() : 0,
                    log != null ? log.getSignups() : 0));
        }

        return new Responses.StatsDto(totalUsers, verifiedUsers, admins, moderators, newUsers,
                totalCompanies, approvedCompanies, pendingCompanies, totalLocations, totalFeedback,
                pendingProofs, pendingFeedback, openSiteFeedback, traffic);
    }

    public Responses.PublicStatsDto publicStats() {
        return new Responses.PublicStatsDto(
                companyRepository.countByStatus(ApprovalStatus.APPROVED),
                feedbackRepository.count(),
                userRepository.countByEmailVerifiedTrue());
    }
}