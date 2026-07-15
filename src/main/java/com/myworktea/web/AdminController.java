package com.myworktea.web;

import com.myworktea.dto.DtoMapper;
import com.myworktea.dto.Requests;
import com.myworktea.dto.Responses;
import com.myworktea.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Full admin panel API. All endpoints require ROLE_ADMIN (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final CategoryService categoryService;
    private final SiteContentService siteContentService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final CompanyService companyService;

    public AdminController(
            AdminService adminService,
            UserService userService,
            CategoryService categoryService,
            SiteContentService siteContentService,
            CurrentUserService currentUserService,
            AuditService auditService,
            CompanyService companyService
    ) {
        this.adminService = adminService;
        this.userService = userService;
        this.categoryService = categoryService;
        this.siteContentService = siteContentService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.companyService = companyService;

    }

    // ---- statistics dashboard ----
    @GetMapping("/stats")
    public Responses.StatsDto stats() {
        return adminService.stats();
    }

    // ---- audit trail (records of approved/rejected workplaces & reviews, deleted users) ----
    @GetMapping("/audit")
    public Page<Responses.AuditLogDto> audit(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 30, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        com.myworktea.domain.AuditCategory cat = null;
        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            cat = com.myworktea.domain.AuditCategory.valueOf(category.toUpperCase());
        }
        return auditService.list(cat, pageable).map(DtoMapper::audit);
    }

    // ---- user management ----
    @GetMapping("/users")
    public Page<Responses.UserDto> users(@RequestParam(required = false) String q,
                                         @PageableDefault(size = 30) Pageable pageable) {
        return adminService.searchUsers(q, pageable).map(DtoMapper::user);
    }

    @PostMapping("/users/{id}/enabled")
    public Responses.UserDto setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return DtoMapper.user(adminService.setEnabled(id, enabled));
    }

    @PostMapping("/users/{id}/flag")
    public Responses.UserDto flag(@PathVariable Long id, @Valid @RequestBody Requests.FlagUserRequest request) {
        return DtoMapper.user(adminService.flagUser(id, request.reason()));
    }

    @DeleteMapping("/users/{id}")
    public Responses.SimpleMessage deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return Responses.SimpleMessage.ok("User deleted");
    }

    // ---- moderator management ----
    @PostMapping("/moderators")
    public Responses.UserDto setModerator(@Valid @RequestBody Requests.ModeratorRequest request) {
        return DtoMapper.user(userService.setModeratorPermissions(request));
    }

    // ---- workplace deletion ----
    @DeleteMapping("/companies/{id}")
    public Responses.SimpleMessage deleteCompany(@PathVariable Long id) {
        adminService.deleteCompany(id);
        return Responses.SimpleMessage.ok("Workplace deleted");
    }

    @PostMapping("/companies")
    public org.springframework.http.ResponseEntity<Responses.CompanyDetailDto> createCompany(
            @Valid @RequestBody Requests.CompanySubmissionRequest request) {
        Responses.CompanyDetailDto dto = DtoMapper.companyDetail(
                companyService.adminCreate(currentUserService.require(), request));
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/companies/{id}")
    public Responses.CompanyDetailDto updateCompany(@PathVariable Long id,
                                                     @Valid @RequestBody Requests.CompanySubmissionRequest request) {
        return DtoMapper.companyDetail(companyService.adminUpdate(id, request));
    }

    // ---- category management ----
    @PostMapping("/categories")
    public Responses.CategoryDto createCategory(@Valid @RequestBody Requests.CategoryRequest request) {
        return DtoMapper.category(categoryService.create(request.name()));
    }

    @DeleteMapping("/categories/{id}")
    public Responses.SimpleMessage deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return Responses.SimpleMessage.ok("Category deleted");
    }

    // ---- "What's new" updates ----
    @PostMapping("/site-updates")
    public Responses.SiteUpdateDto createUpdate(@Valid @RequestBody Requests.SiteUpdateRequest request) {
        return DtoMapper.siteUpdate(siteContentService.createUpdate(currentUserService.require(), request));
    }

    @PutMapping("/site-updates/{id}")
    public Responses.SiteUpdateDto editUpdate(@PathVariable Long id,
                                              @Valid @RequestBody Requests.SiteUpdateRequest request) {
        return DtoMapper.siteUpdate(siteContentService.editUpdate(id, request));
    }

    @DeleteMapping("/site-updates/{id}")
    public Responses.SimpleMessage deleteUpdate(@PathVariable Long id) {
        siteContentService.deleteUpdate(id);
        return Responses.SimpleMessage.ok("Update removed");
    }

    // ---- site feedback inbox ----
    @GetMapping("/site-feedback")
    public Page<Responses.SiteFeedbackDto> siteFeedback(@RequestParam(defaultValue = "false") boolean resolved,
                                                        @PageableDefault(size = 30) Pageable pageable) {
        return siteContentService.listSiteFeedback(resolved, pageable).map(DtoMapper::siteFeedback);
    }

    @PostMapping("/site-feedback/{id}/resolve")
    public Responses.SiteFeedbackDto resolve(@PathVariable Long id, @RequestBody(required = false) Map<String, Boolean> body) {
        boolean resolved = body == null || !Boolean.FALSE.equals(body.get("resolved"));
        return DtoMapper.siteFeedback(siteContentService.resolveSiteFeedback(id, resolved));
    }
}