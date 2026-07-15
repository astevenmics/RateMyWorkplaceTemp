package com.myworktea.web;

import com.myworktea.dto.DtoMapper;
import com.myworktea.dto.Requests;
import com.myworktea.dto.Responses;
import com.myworktea.service.CurrentUserService;
import com.myworktea.service.SiteContentService;
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

    public SiteController(SiteContentService siteContentService, CurrentUserService currentUserService) {
        this.siteContentService = siteContentService;
        this.currentUserService = currentUserService;
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

    @GetMapping("/updates/{id}")
    public Responses.SiteUpdateDto update(@PathVariable Long id) {
        return DtoMapper.siteUpdate(siteContentService.getPublishedUpdate(id));
    }
}