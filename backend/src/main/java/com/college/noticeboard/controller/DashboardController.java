package com.college.noticeboard.controller;

import com.college.noticeboard.dto.DashboardResponse;
import com.college.noticeboard.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public DashboardResponse admin() {
        return dashboardService.admin();
    }

    @GetMapping("/principal")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public DashboardResponse principal() {
        return dashboardService.principal();
    }

    @GetMapping("/hod")
    @PreAuthorize("hasRole('HOD')")
    public DashboardResponse hod() {
        return dashboardService.hod();
    }

    @GetMapping("/faculty")
    @PreAuthorize("hasRole('FACULTY')")
    public DashboardResponse faculty() {
        return dashboardService.faculty();
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public DashboardResponse student() {
        return dashboardService.student();
    }
}
