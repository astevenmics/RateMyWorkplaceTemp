package com.myworktea.web;

import com.myworktea.domain.Company;
import com.myworktea.domain.EmploymentProof;
import com.myworktea.domain.Feedback;
import com.myworktea.domain.User;
import com.myworktea.dto.DtoMapper;
import com.myworktea.dto.Requests;
import com.myworktea.dto.Responses;
import com.myworktea.service.*;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Moderation endpoints. Each action is gated by a specific moderator permission
 * (granted by an admin) OR full admin rights. Coarse access to {@code /api/mod/**}
 * is already restricted to MODERATOR/ADMIN roles in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/mod")
public class ModerationController {

    private final ProofService proofService;
    private final FeedbackService feedbackService;
    private final AdminService adminService;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;
    private final RantService rantService;

    public ModerationController(ProofService proofService, FeedbackService feedbackService,
                                AdminService adminService, FileStorageService fileStorageService,
                                CurrentUserService currentUserService, RantService rantService) {
        this.proofService = proofService;
        this.feedbackService = feedbackService;
        this.adminService = adminService;
        this.fileStorageService = fileStorageService;
        this.currentUserService = currentUserService;
        this.rantService = rantService;
    }

    // ---- proofs (employment proof approval) ----
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_APPROVE_PROOFS')")
    @GetMapping("/proofs/pending")
    public Page<Responses.ProofDto> pendingProofs(@PageableDefault(size = 30) Pageable pageable) {
        return proofService.pending(pageable).map(DtoMapper::proof);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_APPROVE_PROOFS')")
    @GetMapping("/proofs/{id}/file")
    public ResponseEntity<Resource> proofFile(@PathVariable Long id) {
        EmploymentProof proof = proofService.get(id);
        Resource resource = new FileSystemResource(fileStorageService.resolve(proof.getStoredFileName()));
        // Force a download (attachment) rather than rendering inline, so a malicious or
        // corrupted upload is never executed/rendered in the reviewer's browser.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + proof.getOriginalFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_APPROVE_PROOFS')")
    @PostMapping("/proofs/{id}/review")
    public Responses.ProofDto reviewProof(@PathVariable Long id,
                                          @Valid @RequestBody Requests.ReviewDecisionRequest request) {
        User reviewer = currentUserService.require();
        boolean approve = "APPROVE".equalsIgnoreCase(request.decision());
        return DtoMapper.proof(proofService.review(id, approve, request.note(), reviewer));
    }

    // ---- workplace approval ----
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_APPROVE_WORKPLACES')")
    @GetMapping("/companies/pending")
    public Page<Responses.CompanyDetailDto> pendingCompanies(@PageableDefault(size = 30) Pageable pageable) {
        return adminService.pendingCompanies(pageable).map(DtoMapper::companyDetail);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_APPROVE_WORKPLACES')")
    @PostMapping("/companies/{id}/review")
    public Responses.CompanyDetailDto reviewCompany(@PathVariable Long id,
                                                    @Valid @RequestBody Requests.ReviewDecisionRequest request) {
        boolean approve = "APPROVE".equalsIgnoreCase(request.decision());
        Company company = adminService.reviewCompany(id, approve);
        return DtoMapper.companyDetail(company);
    }

    // ---- feedback moderation ----
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_MODERATE_FEEDBACK')")
    @GetMapping("/feedback")
    public Page<Responses.FeedbackDto> feedback(
            @RequestParam(defaultValue = "ALL") String status,
            @PageableDefault(size = 30) Pageable pageable) {
        Page<Feedback> page = "ALL".equalsIgnoreCase(status)
                ? feedbackService.all(pageable)
                : feedbackService.byStatus(com.myworktea.domain.ApprovalStatus.valueOf(status.toUpperCase()), pageable);
        return page.map(DtoMapper::feedback);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_MODERATE_FEEDBACK')")
    @PostMapping("/feedback/{id}/moderate")
    public Responses.FeedbackDto moderate(@PathVariable Long id,
                                          @Valid @RequestBody Requests.ReviewDecisionRequest request) {
        // "REJECT" hides the feedback for a T&C violation; "APPROVE" restores it.
        boolean hide = "REJECT".equalsIgnoreCase(request.decision());
        return DtoMapper.feedback(feedbackService.moderate(id, hide, request.note()));
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_MODERATE_FEEDBACK')")
    @DeleteMapping("/feedback/{id}")
    public Responses.SimpleMessage deleteFeedback(@PathVariable Long id) {
        feedbackService.delete(id);
        return Responses.SimpleMessage.ok("Feedback deleted");
    }

    // ---- anonymous rant moderation ----
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_MODERATE_FEEDBACK')")
    @DeleteMapping("/rants/{id}")
    public Responses.SimpleMessage deleteRant(@PathVariable Long id) {
        rantService.delete(id);
        return Responses.SimpleMessage.ok("Rant deleted");
    }

    // ---- user moderation (flagging) ----
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_MANAGE_USERS')")
    @GetMapping("/users")
    public Page<Responses.UserDto> users(@RequestParam(required = false) String q,
                                         @PageableDefault(size = 30) Pageable pageable) {
        return adminService.searchUsers(q, pageable).map(DtoMapper::user);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MOD_MANAGE_USERS')")
    @PostMapping("/users/{id}/flag")
    public Responses.UserDto flag(@PathVariable Long id, @Valid @RequestBody Requests.FlagUserRequest request) {
        return DtoMapper.user(adminService.flagUser(id, request.reason()));
    }
}