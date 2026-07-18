package com.myworktea.web;

import com.myworktea.domain.VoteType;
import com.myworktea.dto.Requests;
import com.myworktea.dto.Responses;
import com.myworktea.service.CurrentUserService;
import com.myworktea.service.RantService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Anonymous, non-company-specific work rants — no login required to read, post or vote. */
@RestController
@RequestMapping("/api/rants")
public class RantController {

    private final RantService rantService;
    private final CurrentUserService currentUserService;

    public RantController(RantService rantService, CurrentUserService currentUserService) {
        this.rantService = rantService;
        this.currentUserService = currentUserService;
    }

    /** A random sample for the homepage teaser. */
    @GetMapping("/random")
    public List<Responses.RantDto> random(@RequestParam(defaultValue = "6") int limit,
                                          @RequestParam(required = false) String voterId) {
        return rantService.random(limit, resolveVoterId(voterId, false));
    }

    /** Full browse listing — newest-first by default; pass e.g. ?sort=upvotes,desc for "most upvoted". */
    @GetMapping
    public Page<Responses.RantDto> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String voterId) {
        return rantService.list(pageable, resolveVoterId(voterId, false));
    }

    @PostMapping
    public ResponseEntity<Responses.RantDto> submit(@Valid @RequestBody Requests.RantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rantService.submit(request));
    }

    /** Casts/changes/retracts the caller's vote. One vote per voter, logged-in or anonymous. */
    @PostMapping("/{id}/vote")
    public Responses.RantDto vote(@PathVariable Long id, @Valid @RequestBody Requests.VoteRequest request) {
        String voterId = resolveVoterId(request.voterId(), true);
        return rantService.vote(id, voterId, VoteType.valueOf(request.type().toUpperCase()));
    }

    /**
     * A logged-in caller always votes as their account (can't be spoofed by a client-supplied id);
     * an anonymous caller is identified by the id their browser persists in localStorage.
     * {@code required} governs whether a missing anonymous id is an error (voting) or just means
     * "don't highlight anything" (reading).
     */
    private String resolveVoterId(String clientVoterId, boolean required) {
        return currentUserService.current()
                .map(u -> "user:" + u.getId())
                .orElseGet(() -> {
                    if (clientVoterId == null || clientVoterId.isBlank()) {
                        if (required) {
                            throw ApiException.badRequest("Missing voter id");
                        }
                        return null;
                    }
                    return "anon:" + clientVoterId.trim();
                });
    }
}
