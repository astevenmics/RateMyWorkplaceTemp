package com.ratemywork.service;

import com.ratemywork.domain.*;
import com.ratemywork.dto.Requests;
import com.ratemywork.repository.FeedbackRepository;
import com.ratemywork.repository.LocationRepository;
import com.ratemywork.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final LocationRepository locationRepository;
    private final ProofService proofService;
    private final CompanyService companyService;

    public FeedbackService(FeedbackRepository feedbackRepository, LocationRepository locationRepository,
                           ProofService proofService, CompanyService companyService) {
        this.feedbackRepository = feedbackRepository;
        this.locationRepository = locationRepository;
        this.proofService = proofService;
        this.companyService = companyService;
    }

    public Page<Feedback> forLocation(Long locationId, Pageable pageable) {
        return feedbackRepository.findByLocationIdAndStatus(locationId, ApprovalStatus.APPROVED, pageable);
    }

    public Page<Feedback> forCompany(Long companyId, Pageable pageable) {
        return feedbackRepository.findByCompanyIdAndStatus(companyId, ApprovalStatus.APPROVED, pageable);
    }

    @Transactional
    public Feedback submit(User author, Requests.FeedbackRequest req) {
        if (!author.isFullyVerified()) {
            throw ApiException.forbidden("Verify your email and phone number before posting feedback");
        }
        Location location = locationRepository.findById(req.locationId())
                .orElseThrow(() -> ApiException.notFound("Location not found"));

        if (location.getCompany().getStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.badRequest("This workplace is not yet approved for public feedback");
        }
        if (!proofService.canReviewLocation(author, location)) {
            throw ApiException.forbidden(
                    "You need an approved employment proof for this location before leaving feedback");
        }

        Feedback feedback = new Feedback();
        feedback.setAuthor(author);
        feedback.setLocation(location);
        feedback.setCompany(location.getCompany());
        feedback.setRating(req.rating());
        feedback.setTitle(req.title());
        feedback.setBody(req.body());
        feedback.setStatus(ApprovalStatus.APPROVED);
        feedback = feedbackRepository.save(feedback);

        companyService.recomputeAggregates(location);
        return feedback;
    }

    // ---- moderation ----
    public Page<Feedback> all(Pageable pageable) {
        return feedbackRepository.findAll(pageable);
    }

    public Page<Feedback> byStatus(ApprovalStatus status, Pageable pageable) {
        return feedbackRepository.findByStatus(status, pageable);
    }

    @Transactional
    public Feedback moderate(Long id, boolean hide, String note) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Feedback not found"));
        feedback.setStatus(hide ? ApprovalStatus.REJECTED : ApprovalStatus.APPROVED);
        feedback.setModerationNote(note);
        feedback = feedbackRepository.save(feedback);
        companyService.recomputeAggregates(feedback.getLocation());
        return feedback;
    }

    @Transactional
    public void delete(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Feedback not found"));
        Location location = feedback.getLocation();
        feedbackRepository.delete(feedback);
        companyService.recomputeAggregates(location);
    }
}
