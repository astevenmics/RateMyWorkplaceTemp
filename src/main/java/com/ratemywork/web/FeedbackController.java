package com.ratemywork.web;

import com.ratemywork.domain.Feedback;
import com.ratemywork.domain.Location;
import com.ratemywork.domain.User;
import com.ratemywork.dto.DtoMapper;
import com.ratemywork.dto.Requests;
import com.ratemywork.dto.Responses;
import com.ratemywork.repository.LocationRepository;
import com.ratemywork.service.CurrentUserService;
import com.ratemywork.service.FeedbackService;
import com.ratemywork.service.ProofService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final ProofService proofService;
    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;

    public FeedbackController(FeedbackService feedbackService, ProofService proofService,
                              CurrentUserService currentUserService, LocationRepository locationRepository) {
        this.feedbackService = feedbackService;
        this.proofService = proofService;
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
    }

    @GetMapping("/location/{locationId}")
    public Page<Responses.FeedbackDto> forLocation(@PathVariable Long locationId,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return feedbackService.forLocation(locationId, pageable).map(DtoMapper::feedback);
    }

    @GetMapping("/company/{companyId}")
    public Page<Responses.FeedbackDto> forCompany(@PathVariable Long companyId,
                                                  @PageableDefault(size = 20) Pageable pageable) {
        return feedbackService.forCompany(companyId, pageable).map(DtoMapper::feedback);
    }

    /** Tells the frontend whether the current user may leave feedback for a location. */
    @GetMapping("/eligibility/{locationId}")
    public Map<String, Object> eligibility(@PathVariable Long locationId) {
        return currentUserService.current().map(user -> {
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> ApiException.notFound("Location not found"));
            boolean verified = user.isFullyVerified();
            boolean hasProof = proofService.canReviewLocation(user, location);
            return Map.<String, Object>of(
                    "authenticated", true,
                    "verified", verified,
                    "hasApprovedProof", hasProof,
                    "canSubmit", verified && hasProof);
        }).orElse(Map.of("authenticated", false, "canSubmit", false));
    }

    @PostMapping
    public ResponseEntity<Responses.FeedbackDto> submit(@Valid @RequestBody Requests.FeedbackRequest request) {
        User user = currentUserService.require();
        Feedback feedback = feedbackService.submit(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.feedback(feedback));
    }
}
