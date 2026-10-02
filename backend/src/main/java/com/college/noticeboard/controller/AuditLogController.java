package com.college.noticeboard.controller;

import com.college.noticeboard.dto.AuditLogResponse;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.entity.AuditAction;
import com.college.noticeboard.service.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Audit Logs")
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) AuditAction action,
                                               @RequestParam(required = false) Long userId,
                                               @RequestParam(required = false) String entityType,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return auditService.list(action, userId, entityType, page, size);
    }
}
