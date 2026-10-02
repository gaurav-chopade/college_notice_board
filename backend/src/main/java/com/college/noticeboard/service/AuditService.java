package com.college.noticeboard.service;

import com.college.noticeboard.dto.AuditLogResponse;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.entity.AuditAction;
import com.college.noticeboard.entity.AuditLog;
import com.college.noticeboard.entity.User;
import com.college.noticeboard.dto.SimpleMappers;
import com.college.noticeboard.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(User actor, AuditAction action, String entityType, Long entityId, String description) {
        auditLogRepository.save(AuditLog.builder()
                .userId(actor == null ? null : actor.getId())
                .userEmail(actor == null ? "system" : actor.getEmail())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description == null ? null : truncate(description))
                .timestamp(LocalDateTime.now())
                .build());
    }

    public void logSystem(AuditAction action, String entityType, Long entityId, String description) {
        log(null, action, entityType, entityId, description);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(AuditAction action, Long userId, String entityType, int page, int size) {
        Specification<AuditLog> spec = Specification.where(null);
        if (action != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("action"), action));
        }
        if (userId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("userId"), userId));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("entityType"), entityType));
        }
        var result = auditLogRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), clamp(size), Sort.by(Sort.Direction.DESC, "timestamp")));
        return PageResponse.of(result, SimpleMappers::toResponse);
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private static String truncate(String s) {
        return s.length() > 1000 ? s.substring(0, 997) + "..." : s;
    }
}
