package com.college.noticeboard.dto;

import com.college.noticeboard.dto.AttachmentDtos.AttachmentResponse;
import com.college.noticeboard.dto.NoticeDtos.*;
import com.college.noticeboard.entity.Notice;
import com.college.noticeboard.entity.NoticeTarget;

import java.util.List;
import java.util.stream.Collectors;

public final class NoticeMapper {

    private NoticeMapper() {
    }

    public static String excerpt(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 180 ? flat : flat.substring(0, 177) + "...";
    }

    public static String label(NoticeTarget t) {
        String role = t.getTargetRole() == null ? null : pretty(t.getTargetRole().name());
        String dept = t.getDepartment() == null ? null : t.getDepartment().getName();
        return switch (t.getTargetType()) {
            case COLLEGE -> "Entire college";
            case ROLE -> "All " + role;
            case DEPARTMENT -> dept + " (everyone)";
            case ROLE_DEPARTMENT -> dept + " " + role;
            case SEMESTER -> dept + " semester " + t.getSemester() + (role == null ? "" : " " + role);
            case CLASS -> dept + " semester " + t.getSemester() + " class " + t.getClassName();
            case SPECIFIC_USER -> t.getUser() == null ? "Specific user" : t.getUser().getFullName();
        };
    }

    private static String pretty(String roleName) {
        return switch (roleName) {
            case "HOD" -> "HODs";
            case "SUPER_ADMIN" -> "administrators";
            case "PRINCIPAL" -> "principals";
            case "FACULTY" -> "faculty";
            case "STUDENT" -> "students";
            default -> roleName.toLowerCase();
        };
    }

    public static String targetSummary(List<NoticeTarget> targets) {
        long users = targets.stream().filter(t -> t.getUser() != null).count();
        String groups = targets.stream().filter(t -> t.getUser() == null).map(NoticeMapper::label)
                .collect(Collectors.joining(", "));
        if (users == 0) {
            return groups;
        }
        String userPart = users + (users == 1 ? " selected user" : " selected users");
        return groups.isEmpty() ? userPart : groups + ", " + userPart;
    }

    public static TargetResponse toResponse(NoticeTarget t) {
        return new TargetResponse(t.getId(), t.getTargetType(), t.getTargetRole(),
                t.getDepartment() == null ? null : t.getDepartment().getId(),
                t.getDepartment() == null ? null : t.getDepartment().getName(),
                t.getSemester(), t.getClassName(),
                t.getUser() == null ? null : t.getUser().getId(),
                t.getUser() == null ? null : t.getUser().getFullName(), label(t));
    }

    public static NoticeSummaryResponse toSummary(Notice n, long attachmentCount, boolean read, boolean saved) {
        return new NoticeSummaryResponse(n.getId(), n.getTitle(), excerpt(n.getDescription()),
                n.getCategory().getId(), n.getCategory().getName(), n.getPriority(), n.getStatus(),
                n.getCreatedBy().getId(), n.getCreatedBy().getFullName(), n.getCreatedBy().getRole(),
                n.getDepartment() == null ? null : n.getDepartment().getName(),
                n.getPublishDate(), n.getExpiryDate(), n.getPublishedAt(), n.getCreatedAt(),
                targetSummary(n.getTargets()), attachmentCount, read, n.needsAcknowledgement(), saved,
                n.getVisibility(), n.getInstitutionScope());
    }

    public static NoticeDetailResponse toDetail(Notice n, List<AttachmentResponse> attachments, boolean read,
                                                boolean acknowledged, boolean saved, NoticePermissions permissions) {
        return new NoticeDetailResponse(n.getId(), n.getTitle(), n.getDescription(),
                n.getCategory().getId(), n.getCategory().getName(), n.getPriority(), n.getStatus(),
                n.getCreatedBy().getId(), n.getCreatedBy().getFullName(), n.getCreatedBy().getRole(),
                n.getDepartment() == null ? null : n.getDepartment().getId(),
                n.getDepartment() == null ? null : n.getDepartment().getName(),
                n.getPublishDate(), n.getExpiryDate(), n.getPublishedAt(), n.getCreatedAt(), n.getUpdatedAt(),
                n.getRejectionReason(), n.getReviewedBy() == null ? null : n.getReviewedBy().getFullName(),
                n.getReviewedAt(), n.needsAcknowledgement(), n.isAiGenerated(),
                n.getTargets().stream().map(NoticeMapper::toResponse).toList(),
                attachments, read, acknowledged, saved, permissions, n.getVisibility(), n.getInstitutionScope());
    }
}
