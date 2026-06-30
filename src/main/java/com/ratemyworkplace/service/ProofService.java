package com.ratemyworkplace.service;

import com.ratemyworkplace.domain.*;
import com.ratemyworkplace.repository.CompanyRepository;
import com.ratemyworkplace.repository.EmploymentProofRepository;
import com.ratemyworkplace.repository.LocationRepository;
import com.ratemyworkplace.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
public class ProofService {

    /** A user may not have more than one proof per location while it's pending or approved. */
    private static final List<ApprovalStatus> ACTIVE_STATUSES =
            List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED);

    private final EmploymentProofRepository proofRepository;
    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public ProofService(EmploymentProofRepository proofRepository, CompanyRepository companyRepository,
                        LocationRepository locationRepository, FileStorageService fileStorageService,
                        NotificationService notificationService) {
        this.proofRepository = proofRepository;
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    @Transactional
    public EmploymentProof submit(User user, Long companyId, Long locationId, String note, MultipartFile file) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Workplace not found"));

        Location location = null;
        if (locationId != null) {
            location = locationRepository.findById(locationId)
                    .orElseThrow(() -> ApiException.notFound("Location not found"));
            if (!location.getCompany().getId().equals(companyId)) {
                throw ApiException.badRequest("That location does not belong to the selected workplace");
            }
        }

        // Enforce one active (pending/approved) proof per location (or per company for a company-wide proof).
        boolean duplicate = (location != null)
                ? proofRepository.existsByUserIdAndLocationIdAndStatusIn(user.getId(), location.getId(), ACTIVE_STATUSES)
                : proofRepository.existsByUserIdAndCompanyIdAndLocationIsNullAndStatusIn(
                        user.getId(), companyId, ACTIVE_STATUSES);
        if (duplicate) {
            throw ApiException.conflict("You already have a pending or approved proof for this "
                    + (location != null ? "location" : "company") + ". Cancel it first to submit a new one.");
        }

        String stored = fileStorageService.store(file);

        EmploymentProof proof = new EmploymentProof();
        proof.setUser(user);
        proof.setCompany(company);
        proof.setLocation(location);
        proof.setNote(note);
        proof.setStoredFileName(stored);
        proof.setOriginalFileName(file.getOriginalFilename());
        proof.setContentType(file.getContentType());
        proof.setStatus(ApprovalStatus.PENDING);
        return proofRepository.save(proof);
    }

    public List<EmploymentProof> myProofs(User user) {
        return proofRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public Page<EmploymentProof> pending(Pageable pageable) {
        return proofRepository.findByStatus(ApprovalStatus.PENDING, pageable);
    }

    public EmploymentProof get(Long id) {
        return proofRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Proof not found"));
    }

    /** Cancel a still-pending proof. Only the owner may cancel, and only while PENDING. */
    @Transactional
    public void cancel(User user, Long id) {
        EmploymentProof proof = get(id);
        if (!proof.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only cancel your own submissions");
        }
        if (proof.getStatus() != ApprovalStatus.PENDING) {
            throw ApiException.badRequest("Only pending submissions can be cancelled");
        }
        fileStorageService.delete(proof.getStoredFileName());
        proofRepository.delete(proof);
    }

    @Transactional
    public EmploymentProof review(Long id, boolean approve, String note, User reviewer) {
        EmploymentProof proof = get(id);
        proof.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        proof.setReviewNote(note);
        proof.setReviewedBy(reviewer);
        proof.setReviewedAt(Instant.now());
        EmploymentProof saved = proofRepository.save(proof);

        User owner = saved.getUser();
        String locationLabel = saved.getLocation() != null
                ? com.ratemyworkplace.dto.DtoMapper.locationLabel(saved.getLocation()) : null;
        notificationService.notifyProofReviewed(owner.getEmail(), owner.getDisplayName(),
                saved.getCompany().getName(), locationLabel, approve, note);
        return saved;
    }

    /**
     * Whether the user is cleared to leave feedback for a specific location: an approved
     * proof scoped exactly to that location, or a company-wide approved proof.
     */
    public boolean canReviewLocation(User user, Location location) {
        boolean companyWide = proofRepository.existsByUserIdAndCompanyIdAndLocationIsNullAndStatus(
                user.getId(), location.getCompany().getId(), ApprovalStatus.APPROVED);
        boolean locationSpecific = proofRepository.existsByUserIdAndLocationIdAndStatus(
                user.getId(), location.getId(), ApprovalStatus.APPROVED);
        return companyWide || locationSpecific;
    }
}
