package com.ratemywork.service;

import com.ratemywork.domain.*;
import com.ratemywork.repository.CompanyRepository;
import com.ratemywork.repository.EmploymentProofRepository;
import com.ratemywork.repository.LocationRepository;
import com.ratemywork.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
public class ProofService {

    private final EmploymentProofRepository proofRepository;
    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final FileStorageService fileStorageService;

    public ProofService(EmploymentProofRepository proofRepository, CompanyRepository companyRepository,
                        LocationRepository locationRepository, FileStorageService fileStorageService) {
        this.proofRepository = proofRepository;
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.fileStorageService = fileStorageService;
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

    @Transactional
    public EmploymentProof review(Long id, boolean approve, String note, User reviewer) {
        EmploymentProof proof = get(id);
        proof.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        proof.setReviewNote(note);
        proof.setReviewedBy(reviewer);
        proof.setReviewedAt(Instant.now());
        return proofRepository.save(proof);
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
