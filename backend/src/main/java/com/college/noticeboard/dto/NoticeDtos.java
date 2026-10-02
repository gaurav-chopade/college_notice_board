package com.college.noticeboard.dto;

import com.college.noticeboard.entity.AuditAction;
import com.college.noticeboard.entity.InstitutionScope;
import com.college.noticeboard.entity.NoticeVisibility;
import com.college.noticeboard.entity.NoticeStatus;
import com.college.noticeboard.entity.Priority;
import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.TargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class NoticeDtos {

    private NoticeDtos() {
    }

    public record TargetRequest(
            @NotNull TargetType targetType,
            Role targetRole,
            Long departmentId,
            @Min(1) @Max(12) Integer semester,
            @Size(max = 20) String className,
            Long userId) {
    }

    public record TargetResponse(Long id, TargetType targetType, Role targetRole, Long departmentId,
                                 String departmentName, Integer semester, String className,
                                 Long userId, String userName, String label) {
    }

    public record NoticeRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String description,
            @NotNull Long categoryId,
            @NotNull Priority priority,
            LocalDateTime publishDate,
            @Future(message = "Expiry date must be in the future") LocalDateTime expiryDate,
            Boolean acknowledgementRequired,
            @Size(max = 50) List<@Valid TargetRequest> targets,
            NoticeVisibility visibility,
            InstitutionScope institutionScope) {

        /** Before public notices existed every notice was private; keep that default for old clients. */
        public NoticeVisibility visibilityOrDefault() {
            return visibility == null ? NoticeVisibility.PRIVATE : visibility;
        }
    }

    public record RejectRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    /** Query parameters for GET /api/notices. scope = feed | history | mine | approvals | all | saved */
    public record NoticeFilter(
            String scope,
            String q,
            Long categoryId,
            Long departmentId,
            Integer semester,
            Priority priority,
            NoticeStatus status,
            Long creatorId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Boolean unreadOnly) {
    }

    public record NoticeSummaryResponse(
            Long id, String title, String excerpt, Long categoryId, String categoryName, Priority priority,
            NoticeStatus status, Long createdById, String createdByName, Role createdByRole, String departmentName,
            LocalDateTime publishDate, LocalDateTime expiryDate, LocalDateTime publishedAt, LocalDateTime createdAt,
            String targetSummary, long attachmentCount, boolean read, boolean acknowledgementRequired,
            boolean saved, NoticeVisibility visibility, InstitutionScope institutionScope) {
    }

    public record NoticePermissions(boolean canEdit, boolean canDelete, boolean canPublish, boolean canSubmit,
                                    boolean canReview, boolean canAcknowledge, boolean canViewStats,
                                    boolean canArchive, boolean canManageAttachments, boolean canSave) {
    }

    public record NoticeDetailResponse(
            Long id, String title, String description, Long categoryId, String categoryName, Priority priority,
            NoticeStatus status, Long createdById, String createdByName, Role createdByRole,
            Long departmentId, String departmentName, LocalDateTime publishDate, LocalDateTime expiryDate,
            LocalDateTime publishedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
            String rejectionReason, String reviewedByName, LocalDateTime reviewedAt,
            boolean acknowledgementRequired, boolean aiGenerated,
            List<TargetResponse> targets, List<AttachmentDtos.AttachmentResponse> attachments,
            boolean read, boolean acknowledged, boolean saved, NoticePermissions permissions,
            NoticeVisibility visibility, InstitutionScope institutionScope) {
    }

    public record NoticeStatsResponse(Long noticeId, String title, long totalRecipients, long read, long notRead,
                                      long acknowledged, long notAcknowledged) {
    }

    /** Audience as it is being built in the form; nothing is saved. */
    public record RecipientPreviewRequest(
            @NotEmpty(message = "Select at least one target audience") @Size(max = 50) List<@Valid TargetRequest> targets) {
    }

    /**
     * How many active users the audience resolves to right now (creator excluded), split by role,
     * plus the same human-readable labels the saved notice will show.
     */
    public record RecipientPreviewResponse(long total, Map<Role, Long> byRole, List<String> audience) {
    }

    /**
     * One step in a notice's lifecycle. Consecutive identical steps by the same person
     * (for example several edits in a row) are merged and counted in {@code times}.
     */
    public record TimelineEvent(AuditAction action, String actorName, Role actorRole, LocalDateTime at,
                                String note, int times) {
    }
}
