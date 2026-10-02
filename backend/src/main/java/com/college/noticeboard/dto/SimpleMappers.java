package com.college.noticeboard.dto;

import com.college.noticeboard.dto.AttachmentDtos.AttachmentResponse;
import com.college.noticeboard.dto.AuditLogResponse;
import com.college.noticeboard.dto.CategoryDtos.CategoryResponse;
import com.college.noticeboard.dto.DepartmentDtos.DepartmentResponse;
import com.college.noticeboard.dto.NotificationResponse;
import com.college.noticeboard.entity.*;

public final class SimpleMappers {

    private SimpleMappers() {
    }

    public static DepartmentResponse toResponse(Department d) {
        return new DepartmentResponse(d.getId(), d.getName(), d.getCode(), d.getDescription(),
                d.getInstitutionType(), d.getProgramName(), d.getSemesterCount());
    }

    public static CategoryResponse toResponse(NoticeCategory c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }

    public static AttachmentResponse toResponse(Attachment a) {
        return new AttachmentResponse(a.getId(), a.getNotice().getId(), a.getOriginalFileName(), a.getResourceType(),
                a.getFileFormat(), a.getFileSize(), a.getUploadedBy().getFullName(), a.getUploadedAt());
    }

    public static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getNotice() == null ? null : n.getNotice().getId(),
                n.getNotice() == null ? null : n.getNotice().getTitle(),
                n.getNotice() == null ? null : n.getNotice().getPriority(),
                n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt());
    }

    public static AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getUserId(), a.getUserEmail(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDescription(), a.getTimestamp());
    }
}
