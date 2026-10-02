package com.college.noticeboard.dto;

import com.college.noticeboard.entity.AuditAction;

import java.time.LocalDateTime;

public record AuditLogResponse(Long id, Long userId, String userEmail, AuditAction action, String entityType,
                               Long entityId, String description, LocalDateTime timestamp) {
}
