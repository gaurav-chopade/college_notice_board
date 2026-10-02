package com.college.noticeboard.service;

import com.college.noticeboard.dto.NoticeDtos.*;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.dto.TargetOptionsResponse;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.exception.NoticeApprovalException;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.dto.NoticeMapper;
import com.college.noticeboard.service.NotificationService;
import com.college.noticeboard.repository.*;
import com.college.noticeboard.config.CurrentUserService;
import com.college.noticeboard.service.NoticeSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.college.noticeboard.service.NoticeSpecifications.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeCategoryRepository categoryRepository;
    private final AttachmentRepository attachmentRepository;
    private final NotificationRepository notificationRepository;
    private final NoticeAcknowledgementRepository acknowledgementRepository;
    private final NoticeReadRepository readRepository;
    private final SavedNoticeRepository savedRepository;
    private final TargetPolicy targetPolicy;
    private final NoticeAccessService accessService;
    private final RecipientService recipientService;
    private final AttachmentService attachmentService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    // ------------------------------------------------------------------ create / update

    @Transactional
    public NoticeDetailResponse create(NoticeRequest req) {
        return create(currentUserService.currentUser(), req, false);
    }

    /** Every new notice starts as DRAFT, including AI-generated ones. */
    @Transactional
    public NoticeDetailResponse create(User user, NoticeRequest req, boolean aiGenerated) {
        if (user.getRole() == Role.STUDENT) {
            throw new ForbiddenException("Students cannot create notices");
        }
        validateDates(req.publishDate(), req.expiryDate());
        Notice notice = Notice.builder()
                .title(req.title().trim())
                .description(req.description().trim())
                .category(activeCategory(req.categoryId()))
                .priority(req.priority())
                .createdBy(user)
                .department(user.getDepartment())
                .publishDate(req.publishDate())
                .expiryDate(req.expiryDate())
                .acknowledgementRequired(Boolean.TRUE.equals(req.acknowledgementRequired()))
                .aiGenerated(aiGenerated)
                .status(NoticeStatus.DRAFT)
                .build();
        applyVisibilityAndTargets(user, notice, req);
        noticeRepository.save(notice);
        auditService.log(user, aiGenerated ? AuditAction.AI_DRAFT_CREATED : AuditAction.NOTICE_CREATED,
                "Notice", notice.getId(), notice.getTitle());
        return detail(user, notice);
    }

    @Transactional
    public NoticeDetailResponse update(Long id, NoticeRequest req) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        if (!accessService.canEdit(user, notice)) {
            throw new ForbiddenException("You cannot edit this notice in its current state");
        }
        validateDates(notice.getStatus() == NoticeStatus.PUBLISHED ? null : req.publishDate(), req.expiryDate());
        notice.setTitle(req.title().trim());
        notice.setDescription(req.description().trim());
        notice.setCategory(activeCategory(req.categoryId()));
        notice.setPriority(req.priority());
        if (notice.getStatus() != NoticeStatus.PUBLISHED) {
            notice.setPublishDate(req.publishDate());
        }
        notice.setExpiryDate(req.expiryDate());
        notice.setAcknowledgementRequired(Boolean.TRUE.equals(req.acknowledgementRequired()));
        applyVisibilityAndTargets(user, notice, req);
        if (notice.getStatus() == NoticeStatus.REJECTED) {
            // Editing a rejected notice turns it back into a draft that can be resubmitted.
            notice.setStatus(NoticeStatus.DRAFT);
        }
        if (notice.getStatus() == NoticeStatus.SCHEDULED
                && (notice.getPublishDate() == null || !notice.getPublishDate().isAfter(LocalDateTime.now()))) {
            publishNow(notice, user);
        }
        auditService.log(user, AuditAction.NOTICE_EDITED, "Notice", id, notice.getTitle());
        return detail(user, notice);
    }

    /**
     * Private notices need at least one target. Public notices need a wing (Engineering,
     * Polytechnic or both) and may have targets too, to notify members. HODs and teachers may only
     * publish for their own department's wing; teachers' public notices still go to their HOD for
     * approval like any other teacher notice. Students never reach this point.
     */
    private void applyVisibilityAndTargets(User user, Notice notice, NoticeRequest req) {
        List<TargetRequest> targets = req.targets() == null ? List.of() : req.targets();
        NoticeVisibility visibility = req.visibilityOrDefault();
        if (visibility == NoticeVisibility.PRIVATE) {
            if (targets.isEmpty()) {
                throw new BadRequestException("Select at least one target audience");
            }
            notice.setInstitutionScope(null);
        } else {
            InstitutionScope scope = req.institutionScope();
            if (scope == null) {
                throw new BadRequestException("Choose Engineering, Polytechnic or both for a public notice");
            }
            if (user.getRole() == Role.HOD || user.getRole() == Role.FACULTY) {
                InstitutionType own = user.getInstitutionType();
                if (own == null || scope != InstitutionScope.of(own)) {
                    throw new ForbiddenException("You can publish public notices for "
                            + (own == InstitutionType.POLYTECHNIC ? "Polytechnic" : "Engineering") + " only");
                }
            }
            notice.setInstitutionScope(scope);
        }
        notice.setVisibility(visibility);
        notice.replaceTargets(targets.isEmpty() ? new ArrayList<>() : targetPolicy.buildTargets(user, targets));
    }

    // ------------------------------------------------------------------ lifecycle

    @Transactional
    public NoticeDetailResponse publish(Long id) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        if (!accessService.canPublish(user, notice)) {
            throw new ForbiddenException(user.getRole() == Role.FACULTY
                    ? "Faculty notices must be submitted for HOD approval"
                    : "You cannot publish this notice in its current state");
        }
        releaseOrSchedule(notice, user);
        return detail(user, notice);
    }

    @Transactional
    public NoticeDetailResponse submit(Long id) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        if (!accessService.canSubmit(user, notice)) {
            throw new NoticeApprovalException("Only your own draft or rejected notices can be submitted for approval");
        }
        if (notice.getDepartment() == null) {
            throw new NoticeApprovalException("Your account is not linked to a department, so no HOD can approve it");
        }
        ensureNotExpired(notice);
        notice.setStatus(NoticeStatus.PENDING_APPROVAL);
        notice.setRejectionReason(null);
        notice.setReviewedBy(null);
        notice.setReviewedAt(null);
        auditService.log(user, AuditAction.NOTICE_SUBMITTED, "Notice", id, notice.getTitle());
        notificationService.notifyHodsOfSubmission(notice);
        return detail(user, notice);
    }

    @Transactional
    public NoticeDetailResponse approve(Long id) {
        User hod = currentUserService.currentUser();
        Notice notice = load(id);
        if (!accessService.canReview(hod, notice)) {
            throw new NoticeApprovalException("Only the HOD of this department can approve a pending notice");
        }
        ensureNotExpired(notice);
        notice.setReviewedBy(hod);
        notice.setReviewedAt(LocalDateTime.now());
        notice.setStatus(NoticeStatus.APPROVED);
        auditService.log(hod, AuditAction.NOTICE_APPROVED, "Notice", id, notice.getTitle());
        releaseOrSchedule(notice, hod);
        notificationService.notifyUser(notice.getCreatedBy(), notice, "Approved: " + notice.getTitle()
                + (notice.getStatus() == NoticeStatus.SCHEDULED ? " (scheduled)" : " (published)"));
        return detail(hod, notice);
    }

    @Transactional
    public NoticeDetailResponse reject(Long id, String reason) {
        User hod = currentUserService.currentUser();
        Notice notice = load(id);
        if (!accessService.canReview(hod, notice)) {
            throw new NoticeApprovalException("Only the HOD of this department can reject a pending notice");
        }
        notice.setStatus(NoticeStatus.REJECTED);
        notice.setRejectionReason(reason.trim());
        notice.setReviewedBy(hod);
        notice.setReviewedAt(LocalDateTime.now());
        auditService.log(hod, AuditAction.NOTICE_REJECTED, "Notice", id,
                notice.getTitle() + NoticeTimelineService.REASON_SEPARATOR + reason.trim());
        notificationService.notifyUser(notice.getCreatedBy(), notice, "Rejected: " + notice.getTitle() + ". Reason: " + reason);
        return detail(hod, notice);
    }

    @Transactional
    public NoticeDetailResponse archive(Long id) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        if (!accessService.canArchive(user, notice)) {
            throw new ForbiddenException("You cannot archive this notice");
        }
        notice.setStatus(NoticeStatus.ARCHIVED);
        auditService.log(user, AuditAction.NOTICE_ARCHIVED, "Notice", id, notice.getTitle());
        return detail(user, notice);
    }

    @Transactional
    public void delete(Long id) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        if (!accessService.canDelete(user, notice)) {
            throw new ForbiddenException("You cannot delete this notice");
        }
        String title = notice.getTitle();
        attachmentService.deleteAllForNotice(notice);   // Cloudinary first, then metadata
        notificationRepository.deleteByNoticeId(id);
        acknowledgementRepository.deleteByNoticeId(id);
        readRepository.deleteByNoticeId(id);
        savedRepository.deleteByNoticeId(id);
        noticeRepository.delete(notice);
        auditService.log(user, AuditAction.NOTICE_DELETED, "Notice", id, title);
    }

    /** Called by the scheduler: SCHEDULED → PUBLISHED and PUBLISHED → EXPIRED. */
    @Transactional
    public int[] processDueNotices(LocalDateTime now) {
        int published = 0;
        for (Notice n : noticeRepository.findByStatusAndPublishDateLessThanEqual(NoticeStatus.SCHEDULED, now)) {
            if (n.getExpiryDate() != null && !n.getExpiryDate().isAfter(now)) {
                n.setStatus(NoticeStatus.EXPIRED);
                auditService.logSystem(AuditAction.NOTICE_EXPIRED, "Notice", n.getId(), "Expired before it was published");
                continue;
            }
            publishNow(n, null);
            published++;
        }
        int expired = 0;
        for (Notice n : noticeRepository.findByStatusAndExpiryDateLessThanEqual(NoticeStatus.PUBLISHED, now)) {
            n.setStatus(NoticeStatus.EXPIRED);
            auditService.logSystem(AuditAction.NOTICE_EXPIRED, "Notice", n.getId(), n.getTitle());
            expired++;
        }
        return new int[]{published, expired};
    }

    private void releaseOrSchedule(Notice notice, User actor) {
        ensureNotExpired(notice);
        if (notice.getPublishDate() != null && notice.getPublishDate().isAfter(LocalDateTime.now())) {
            notice.setStatus(NoticeStatus.SCHEDULED);
            auditService.log(actor, AuditAction.NOTICE_SCHEDULED, "Notice", notice.getId(),
                    notice.getTitle() + " for " + notice.getPublishDate());
        } else {
            publishNow(notice, actor);
        }
    }

    private void publishNow(Notice notice, User actor) {
        LocalDateTime now = LocalDateTime.now();
        notice.setStatus(NoticeStatus.PUBLISHED);
        notice.setPublishedAt(now);
        if (notice.getPublishDate() == null || notice.getPublishDate().isAfter(now)) {
            notice.setPublishDate(now);
        }
        int recipients = notificationService.notifyPublished(notice);
        auditService.log(actor, AuditAction.NOTICE_PUBLISHED, "Notice", notice.getId(),
                notice.getTitle() + " → " + recipients + " recipient(s)");
    }

    // ------------------------------------------------------------------ reading

    @Transactional
    public NoticeDetailResponse get(Long id) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        accessService.assertCanView(user, notice);
        if (notice.getStatus() == NoticeStatus.PUBLISHED && accessService.isRecipient(user, notice)
                && !accessService.isCreator(user, notice)) {
            recordRead(user, notice);
        }
        return detail(user, notice);
    }

    @Transactional(readOnly = true)
    public PageResponse<NoticeSummaryResponse> list(NoticeFilter f, int page, int size) {
        User user = currentUserService.currentUser();
        String scope = f.scope() == null ? "feed" : f.scope().toLowerCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        boolean admin = user.getRole() == Role.SUPER_ADMIN;

        Specification<Notice> spec = switch (scope) {
            case "feed" -> admin ? activeAt(now) : activeAt(now).and(targetedTo(user));
            case "history" -> {
                Specification<Notice> s = statusIn(List.of(NoticeStatus.PUBLISHED, NoticeStatus.EXPIRED));
                yield admin ? s : s.and(targetedTo(user));
            }
            case "mine" -> {
                if (user.getRole() == Role.STUDENT) {
                    throw new ForbiddenException("Students do not create notices");
                }
                Specification<Notice> s = createdBy(user.getId());
                yield f.status() == null ? s : s.and(status(f.status()));
            }
            case "approvals" -> {
                if (user.getRole() != Role.HOD || user.getDepartmentId() == null) {
                    throw new ForbiddenException("Only HODs can review faculty notices");
                }
                yield createdByRole(Role.FACULTY).and(inDepartment(user.getDepartmentId()))
                        .and(status(f.status() == null ? NoticeStatus.PENDING_APPROVAL : f.status()))
                        .and(statusIn(EnumSet.complementOf(EnumSet.of(NoticeStatus.DRAFT))));
            }
            case "saved" -> {
                // Same visibility as canSave: still out to me, mine, or everything for the admin.
                Specification<Notice> visible = admin ? Specification.where(null)
                        : statusIn(List.of(NoticeStatus.PUBLISHED, NoticeStatus.EXPIRED)).and(targetedTo(user))
                                .or(createdBy(user.getId())).or(publiclyVisible(now));
                yield savedBy(user.getId()).and(visible);
            }
            case "all" -> {
                if (!admin) {
                    throw new ForbiddenException("Only the super admin can list all notices");
                }
                yield f.status() == null ? Specification.where(null) : status(f.status());
            }
            default -> throw new BadRequestException("Unknown scope '" + f.scope() + "'. Use feed, history, mine, approvals, saved or all");
        };

        spec = spec.and(textSearch(f.q())).and(category(f.categoryId())).and(priority(f.priority()))
                .and(departmentFilter(f.departmentId())).and(semesterFilter(f.semester()))
                .and(creator(f.creatorId())).and(createdBetween(f.fromDate(), f.toDate()));
        if (Boolean.TRUE.equals(f.unreadOnly())) {
            spec = spec.and(notReadBy(user.getId()));
        }

        Sort sort = scope.equals("feed") || scope.equals("history") || scope.equals("saved")
                ? Sort.by(Sort.Order.desc("publishDate"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        Page<Notice> result = noticeRepository.findAll(spec, pageable);
        return PageResponse.of(result, summaries(user, result.getContent()));
    }

    /** Builds summaries with batched attachment counts and read flags (no N+1 queries for those). */
    public List<NoticeSummaryResponse> summaries(User user, List<Notice> notices) {
        if (notices.isEmpty()) {
            return List.of();
        }
        List<Long> ids = notices.stream().map(Notice::getId).toList();
        Map<Long, Long> attachmentCounts = attachmentRepository.countByNoticeIds(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Set<Long> read = new HashSet<>(readRepository.findReadNoticeIds(user.getId(), ids));
        Set<Long> saved = new HashSet<>(savedRepository.findSavedNoticeIds(user.getId(), ids));
        return notices.stream()
                .map(n -> NoticeMapper.toSummary(n, attachmentCounts.getOrDefault(n.getId(), 0L),
                        read.contains(n.getId()) || accessService.isCreator(user, n), saved.contains(n.getId())))
                .toList();
    }

    @Transactional
    public NoticeDetailResponse acknowledge(Long id) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        accessService.assertCanView(user, notice);
        if (!accessService.canAcknowledge(user, notice)) {
            throw new BadRequestException("This notice does not need your acknowledgement");
        }
        recordRead(user, notice);
        if (!acknowledgementRepository.existsByNoticeIdAndUserId(id, user.getId())) {
            acknowledgementRepository.save(NoticeAcknowledgement.builder()
                    .notice(notice).user(user).acknowledgedAt(LocalDateTime.now()).build());
            auditService.log(user, AuditAction.NOTICE_ACKNOWLEDGED, "Notice", id, notice.getTitle());
        }
        return detail(user, notice);
    }

    /** Adds the notice to the caller's saved list. Saving twice is harmless. */
    @Transactional
    public NoticeDetailResponse save(Long id) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        accessService.assertCanView(user, notice);
        if (!accessService.canSave(user, notice)) {
            throw new BadRequestException("This notice can be saved once it is published");
        }
        if (!savedRepository.existsByNoticeIdAndUserId(id, user.getId())) {
            savedRepository.save(SavedNotice.builder().notice(notice).user(user).savedAt(LocalDateTime.now()).build());
        }
        return detail(user, notice);
    }

    /** Removes the notice from the caller's saved list, even if they can no longer see the notice. */
    @Transactional
    public void unsave(Long id) {
        User user = currentUserService.currentUser();
        savedRepository.deleteByNoticeIdAndUserId(id, user.getId());
    }

    @Transactional
    public void markRead(Long id) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        accessService.assertCanView(user, notice);
        if (accessService.isRecipient(user, notice) && !accessService.isCreator(user, notice)) {
            recordRead(user, notice);
        }
    }

    @Transactional(readOnly = true)
    public NoticeStatsResponse stats(Long id) {
        User user = currentUserService.currentUser();
        Notice notice = load(id);
        if (!accessService.canViewStats(user, notice)) {
            throw new ForbiddenException("You cannot view statistics for this notice");
        }
        return statsFor(notice);
    }

    /** Read/acknowledged counts restricted to intended recipients only. */
    public NoticeStatsResponse statsFor(Notice notice) {
        Set<Long> recipients = recipientService.recipientIds(notice);
        long read = readRepository.findUserIdsByNoticeId(notice.getId()).stream().filter(recipients::contains).count();
        long ack = acknowledgementRepository.findUserIdsByNoticeId(notice.getId()).stream().filter(recipients::contains).count();
        long total = recipients.size();
        return new NoticeStatsResponse(notice.getId(), notice.getTitle(), total, read, total - read, ack,
                notice.needsAcknowledgement() ? total - ack : 0);
    }

    /**
     * Resolves an unsaved audience to a recipient count. The targets go through the same
     * {@link TargetPolicy} validation and authorisation as on save, so the preview can never
     * reveal the size of an audience the caller is not allowed to address.
     */
    @Transactional(readOnly = true)
    public RecipientPreviewResponse previewRecipients(RecipientPreviewRequest req) {
        User user = currentUserService.currentUser();
        List<NoticeTarget> targets = targetPolicy.buildTargets(user, req.targets());
        List<User> recipients = recipientService.resolve(targets, user.getId());
        Map<Role, Long> byRole = new EnumMap<>(Role.class);
        for (User u : recipients) {
            byRole.merge(u.getRole(), 1L, Long::sum);
        }
        List<String> audience = targets.stream().map(NoticeMapper::label).toList();
        return new RecipientPreviewResponse(recipients.size(), byRole, audience);
    }

    @Transactional(readOnly = true)
    public TargetOptionsResponse targetOptions() {
        return targetPolicy.optionsFor(currentUserService.currentUser());
    }

    // ------------------------------------------------------------------ helpers

    private void recordRead(User user, Notice notice) {
        if (!readRepository.existsByNoticeIdAndUserId(notice.getId(), user.getId())) {
            readRepository.save(NoticeRead.builder().notice(notice).user(user).readAt(LocalDateTime.now()).build());
        }
        notificationRepository.markReadForNotice(user.getId(), notice.getId());
    }

    private NoticeDetailResponse detail(User user, Notice notice) {
        boolean read = accessService.isCreator(user, notice)
                || readRepository.existsByNoticeIdAndUserId(notice.getId(), user.getId());
        boolean acknowledged = acknowledgementRepository.existsByNoticeIdAndUserId(notice.getId(), user.getId());
        boolean saved = savedRepository.existsByNoticeIdAndUserId(notice.getId(), user.getId());
        return NoticeMapper.toDetail(notice, attachmentService.listForAuthorisedNotice(notice.getId()),
                read, acknowledged, saved, accessService.permissions(user, notice));
    }

    private Notice load(Long id) {
        return noticeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notice not found"));
    }

    private NoticeCategory activeCategory(Long id) {
        NoticeCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Category not found"));
        if (!c.isActive()) {
            throw new BadRequestException("Category " + c.getName() + " is inactive");
        }
        return c;
    }

    private static void validateDates(LocalDateTime publish, LocalDateTime expiry) {
        if (expiry != null && !expiry.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Expiry date must be in the future");
        }
        if (publish != null && expiry != null && !expiry.isAfter(publish)) {
            throw new BadRequestException("Expiry date must be after the publish date");
        }
    }

    private static void ensureNotExpired(Notice n) {
        if (n.getExpiryDate() != null && !n.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("The expiry date has already passed. Edit the notice and set a new expiry date");
        }
    }
}
