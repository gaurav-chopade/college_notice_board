package com.college.noticeboard.service;

import com.college.noticeboard.dto.NoticeDtos.NoticePermissions;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.service.TargetMatcher;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Central, backend-enforced authorisation for notices. Every read path (list, detail,
 * attachments, stats) goes through here, so changing a notice ID in the URL cannot bypass it.
 */
@Service
public class NoticeAccessService {

    private static final Set<NoticeStatus> VISIBLE_TO_RECIPIENTS = EnumSet.of(NoticeStatus.PUBLISHED, NoticeStatus.EXPIRED);

    public boolean isCreator(User u, Notice n) {
        return Objects.equals(n.getCreatedBy().getId(), u.getId());
    }

    public boolean isRecipient(User u, Notice n) {
        return TargetMatcher.matchesAny(n.getTargets(), u);
    }

    /** HOD of the department in which a faculty member raised the notice. */
    public boolean isReviewingHod(User u, Notice n) {
        return u.getRole() == Role.HOD
                && n.getCreatedBy().getRole() == Role.FACULTY
                && n.getDepartment() != null
                && Objects.equals(n.getDepartment().getId(), u.getDepartmentId())
                && n.getStatus() != NoticeStatus.DRAFT;
    }

    public boolean canView(User u, Notice n) {
        if (u.getRole() == Role.SUPER_ADMIN || isCreator(u, n) || isReviewingHod(u, n)) {
            return true;
        }
        return (VISIBLE_TO_RECIPIENTS.contains(n.getStatus()) && isRecipient(u, n))
                || n.isPubliclyVisible(java.time.LocalDateTime.now());
    }

    public void assertCanView(User u, Notice n) {
        if (!canView(u, n)) {
            throw new ForbiddenException("You are not authorized to view this notice");
        }
    }

    public boolean canEdit(User u, Notice n) {
        if (!isCreator(u, n)) {
            return false;
        }
        if (u.getRole() == Role.FACULTY) {
            return n.getStatus() == NoticeStatus.DRAFT || n.getStatus() == NoticeStatus.REJECTED;
        }
        return EnumSet.of(NoticeStatus.DRAFT, NoticeStatus.SCHEDULED, NoticeStatus.PUBLISHED).contains(n.getStatus());
    }

    public boolean canDelete(User u, Notice n) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        if (!isCreator(u, n)) {
            return false;
        }
        return u.getRole() != Role.FACULTY
                || n.getStatus() == NoticeStatus.DRAFT || n.getStatus() == NoticeStatus.REJECTED;
    }

    public boolean canPublish(User u, Notice n) {
        return isCreator(u, n)
                && EnumSet.of(Role.SUPER_ADMIN, Role.PRINCIPAL, Role.HOD).contains(u.getRole())
                && n.getStatus() == NoticeStatus.DRAFT;
    }

    public boolean canSubmit(User u, Notice n) {
        return isCreator(u, n) && u.getRole() == Role.FACULTY
                && (n.getStatus() == NoticeStatus.DRAFT || n.getStatus() == NoticeStatus.REJECTED);
    }

    public boolean canReview(User u, Notice n) {
        return isReviewingHod(u, n) && n.getStatus() == NoticeStatus.PENDING_APPROVAL;
    }

    public boolean canAcknowledge(User u, Notice n) {
        return n.getStatus() == NoticeStatus.PUBLISHED && n.needsAcknowledgement()
                && !isCreator(u, n) && isRecipient(u, n);
    }

    public boolean canViewStats(User u, Notice n) {
        return u.getRole() == Role.SUPER_ADMIN || isCreator(u, n) || isReviewingHod(u, n);
    }

    public boolean canArchive(User u, Notice n) {
        return (u.getRole() == Role.SUPER_ADMIN || isCreator(u, n))
                && (n.getStatus() == NoticeStatus.PUBLISHED || n.getStatus() == NoticeStatus.EXPIRED);
    }

    /**
     * A notice can be saved by anyone who will keep seeing it: its recipients once it is out
     * (published or expired), its creator, and the super admin. The saved list applies the same rule.
     */
    public boolean canSave(User u, Notice n) {
        return u.getRole() == Role.SUPER_ADMIN || isCreator(u, n)
                || (VISIBLE_TO_RECIPIENTS.contains(n.getStatus()) && isRecipient(u, n))
                || n.isPubliclyVisible(java.time.LocalDateTime.now());
    }

    public NoticePermissions permissions(User u, Notice n) {
        return new NoticePermissions(canEdit(u, n), canDelete(u, n), canPublish(u, n), canSubmit(u, n),
                canReview(u, n), canAcknowledge(u, n), canViewStats(u, n), canArchive(u, n), canEdit(u, n),
                canSave(u, n));
    }
}
