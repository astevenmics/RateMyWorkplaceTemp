package com.ratemywork.web;

import com.ratemywork.dto.Responses;
import com.ratemywork.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final AdminService adminService;

    public StatsController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Lightweight, non-sensitive counters shown on the public home page. */
    @GetMapping("/public")
    public Responses.PublicStatsDto publicStats() {
        return adminService.publicStats();
    }
}
