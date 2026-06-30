package com.ratemyworkplace.web;

import com.ratemyworkplace.domain.EmploymentProof;
import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.dto.DtoMapper;
import com.ratemyworkplace.dto.Responses;
import com.ratemyworkplace.service.CurrentUserService;
import com.ratemyworkplace.service.ProofService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/proofs")
public class ProofController {

    private final ProofService proofService;
    private final CurrentUserService currentUserService;

    public ProofController(ProofService proofService, CurrentUserService currentUserService) {
        this.proofService = proofService;
        this.currentUserService = currentUserService;
    }

    /**
     * Submit an employment proof (PDF/PNG/JPG) for a specific company &amp; (optional) location.
     * Multipart so the document can be attached directly from the browser form.
     */
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Responses.ProofDto> submit(
            @RequestParam Long companyId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String note,
            @RequestParam("file") MultipartFile file) {
        User user = currentUserService.requireVerified();
        EmploymentProof proof = proofService.submit(user, companyId, locationId, note, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.proof(proof));
    }

    @GetMapping("/mine")
    public List<Responses.ProofDto> mine() {
        User user = currentUserService.require();
        return proofService.myProofs(user).stream().map(DtoMapper::proof).toList();
    }
}
