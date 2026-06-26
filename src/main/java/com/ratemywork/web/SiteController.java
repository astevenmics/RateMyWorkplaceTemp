package com.ratemywork.web;

import com.ratemywork.dto.DtoMapper;
import com.ratemywork.dto.Requests;
import com.ratemywork.dto.Responses;
import com.ratemywork.service.AdminService;
import com.ratemywork.service.CurrentUserService;
import com.ratemywork.service.SiteContentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public endpoints for the footer: submit site feedback &amp; read "What's new" updates. */
@RestController
@RequestMapping("/api/site")
public class SiteController {

    private final SiteContentService siteContentService;
    private final CurrentUserService currentUserService;
    private final AdminService adminService;

    public SiteController(SiteContentService siteContentService, CurrentUserService currentUserService,
                          AdminService adminService) {
        this.siteContentService = siteContentService;
        this.currentUserService = currentUserService;
        this.adminService = adminService;
    }

    @PostMapping("/feedback")
    public ResponseEntity<Responses.SimpleMessage> submitSiteFeedback(
            @Valid @RequestBody Requests.SiteFeedbackRequest request) {
        siteContentService.submitSiteFeedback(currentUserService.current().orElse(null), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Responses.SimpleMessage.ok("Thanks! Your feedback was sent to the admins."));
    }

    @GetMapping("/updates")
    public Page<Responses.SiteUpdateDto> updates(@PageableDefault(size = 10) Pageable pageable) {
        return siteContentService.listUpdates(pageable).map(DtoMapper::siteUpdate);
    }

    @GetMapping("/updates/latest")
    public List<Responses.SiteUpdateDto> latest() {
        return siteContentService.latestUpdates().stream().map(DtoMapper::siteUpdate).toList();
    }
}
