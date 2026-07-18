package com.myworktea.web;

import com.myworktea.config.ClientIpResolver;
import com.myworktea.domain.Role;
import com.myworktea.domain.VoteType;
import com.myworktea.dto.Requests;
import com.myworktea.dto.Responses;
import com.myworktea.service.CurrentUserService;
import com.myworktea.service.RantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Anonymous, non-company-specific work rants — no login required to read, post or vote. */
@RestController
@RequestMapping("/api/rants")
public class RantController {

    private final RantService rantService;
    private final CurrentUserService currentUserService;
    private final ClientIpResolver clientIpResolver;

    public RantController(RantService rantService, CurrentUserService currentUserService,
                          ClientIpResolver clientIpResolver) {
        this.rantService = rantService;
        this.currentUserService = currentUserService;
        this.clientIpResolver = clientIpResolver;
    }

    /** A random sample for the homepage teaser. */
    @GetMapping("/random")
    public List<Responses.RantDto> random(@RequestParam(defaultValue = "6") int limit, HttpServletRequest request) {
        return rantService.random(limit, resolveIdentity(request));
    }

    /** Full browse listing — newest-first by default; pass e.g. ?sort=upvotes,desc for "most upvoted". */
    @GetMapping
    public Page<Responses.RantDto> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest request) {
        return rantService.list(pageable, resolveIdentity(request));
    }

    @PostMapping
    public ResponseEntity<Responses.RantDto> submit(@Valid @RequestBody Requests.RantRequest request,
                                                     HttpServletRequest httpRequest) {
        boolean exempt = currentUserService.current().map(u -> u.getRole() == Role.ADMIN).orElse(false);
        Responses.RantDto dto = rantService.submit(request, resolveIdentity(httpRequest), exempt);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /** Casts/changes/retracts the caller's vote. One vote per voter, logged-in or anonymous. */
    @PostMapping("/{id}/vote")
    public Responses.RantDto vote(@PathVariable Long id, @Valid @RequestBody Requests.VoteRequest request,
                                  HttpServletRequest httpRequest) {
        return rantService.vote(id, resolveIdentity(httpRequest), VoteType.valueOf(request.type().toUpperCase()));
    }

    /**
     * A logged-in caller is identified by their account (can't be spoofed). An anonymous caller is
     * identified by a hash of their IP address — deliberately server-derived rather than trusting
     * anything the client sends, since a client-supplied id (e.g. one stashed in localStorage) is
     * trivially reset by opening a fresh incognito window, defeating one-vote/one-post-per-person.
     * The tradeoff is that visitors sharing one IP (an office network) share one vote/cooldown too.
     */
    private String resolveIdentity(HttpServletRequest request) {
        return currentUserService.current()
                .map(u -> "user:" + u.getId())
                .orElseGet(() -> "ip:" + sha256(clientIpResolver.resolve(request)));
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
