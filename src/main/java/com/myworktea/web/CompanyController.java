package com.myworktea.web;

import com.myworktea.domain.Company;
import com.myworktea.domain.User;
import com.myworktea.dto.DtoMapper;
import com.myworktea.dto.Requests;
import com.myworktea.dto.Responses;
import com.myworktea.service.CompanyService;
import com.myworktea.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final CurrentUserService currentUserService;

    public CompanyController(CompanyService companyService, CurrentUserService currentUserService) {
        this.companyService = companyService;
        this.currentUserService = currentUserService;
    }

    /** Public browse/search: keyword (location, name, keyword), category filter, sort, 30/page. */
    @GetMapping
    public Page<Responses.CompanySummaryDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "top") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return companyService.search(q, categoryId, sort, page, size).map(DtoMapper::companySummary);
    }

    @GetMapping("/top")
    public List<Responses.CompanySummaryDto> top(@RequestParam(defaultValue = "6") int limit) {
        return companyService.topRated(limit).stream().map(DtoMapper::companySummary).toList();
    }

    @GetMapping("/{id}")
    public Responses.CompanyDetailDto get(@PathVariable Long id) {
        return DtoMapper.companyDetail(companyService.getApproved(id));
    }

    @PostMapping
    public ResponseEntity<Responses.CompanyDetailDto> submit(
            @Valid @RequestBody Requests.CompanySubmissionRequest request) {
        User user = currentUserService.requireVerified();
        Company company = companyService.submit(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.companyDetail(company));
    }
}