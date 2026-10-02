package com.college.noticeboard.controller;

import com.college.noticeboard.dto.RegistrationDtos.*;
import com.college.noticeboard.dto.UserDtos.UserResponse;
import com.college.noticeboard.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Account requests")
@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','PRINCIPAL','HOD','FACULTY')")
public class RegistrationController {

    private final RegistrationService registrationService;

    @Operation(summary = "Pending account requests the caller may review",
            description = "Teachers: students of classes they teach. HODs: teachers and students of their department. "
                    + "Principal: HODs and requests with no lower approver. Super admin: all.")
    @GetMapping("/pending")
    public List<PendingRegistration> pending() {
        return registrationService.queue();
    }

    @GetMapping("/pending/count")
    public Map<String, Long> pendingCount() {
        return Map.of("count", registrationService.queueCount());
    }

    @PostMapping("/{id}/approve")
    public UserResponse approve(@PathVariable Long id) {
        return registrationService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public UserResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRegistrationRequest request) {
        return registrationService.reject(id, request.reason());
    }
}
