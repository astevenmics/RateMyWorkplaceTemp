package com.myworktea.web;

import com.myworktea.dto.DtoMapper;
import com.myworktea.dto.Requests;
import com.myworktea.dto.Responses;
import com.myworktea.service.RantService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Anonymous, non-company-specific work rants — no login required to read or post. */
@RestController
@RequestMapping("/api/rants")
public class RantController {

    private final RantService rantService;

    public RantController(RantService rantService) {
        this.rantService = rantService;
    }

    /** A random sample for the homepage teaser. */
    @GetMapping("/random")
    public List<Responses.RantDto> random(@RequestParam(defaultValue = "6") int limit) {
        return rantService.random(limit).stream().map(DtoMapper::rant).toList();
    }

    /** Full, newest-first browse listing. */
    @GetMapping
    public Page<Responses.RantDto> recent(@PageableDefault(size = 20) Pageable pageable) {
        return rantService.recent(pageable).map(DtoMapper::rant);
    }

    @PostMapping
    public ResponseEntity<Responses.RantDto> submit(@Valid @RequestBody Requests.RantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.rant(rantService.submit(request)));
    }
}
