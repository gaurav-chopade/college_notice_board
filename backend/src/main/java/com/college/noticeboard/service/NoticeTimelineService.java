package com.college.noticeboard.service;

import com.college.noticeboard.config.CurrentUserService;
import com.college.noticeboard.dto.NoticeDtos.TimelineEvent;
import com.college.noticeboard.entity.AuditAction;
import com.college.noticeboard.entity.AuditLog;
import com.college.noticeboard.entity.Notice;
import com.college.noticeboard.entity.User;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.repository.AuditLogRepository;
import com.college.noticeboard.repository.NoticeRepository;
import com.college.noticeboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a notice's lifecycle timeline (created → submitted → approved/rejected → published …)
 * from the audit log, so there is a single source of truth for "who did what, when".
 * Only people who manage the notice (creator, reviewing HOD, super admin) can see it;
 * recipients do not see internal review history.
 */
@Service
@RequiredArgsConstructor
public class NoticeTimelineService {

    /** Written between the title and the reason in NOTICE_REJECTED audit entries. */
    public static final String REASON_SEPARATOR = " — Reason: ";

    private static final Set<AuditAction> LIFECYCLE = EnumSet.of(
            AuditAction.NOTICE_CREATED, AuditAction.AI_DRAFT_CREATED, AuditAction.NOTICE_EDITED,
            AuditAction.NOTICE_SUBMITTED, AuditAction.NOTICE_APPROVED, AuditAction.NOTICE_REJECTED,
            AuditAction.NOTICE_SCHEDULED, AuditAction.NOTICE_PUBLISHED, AuditAction.NOTICE_EXPIRED,
            AuditAction.NOTICE_ARCHIVED);

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final NoticeRepository noticeRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final NoticeAccessService accessService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<TimelineEvent> timeline(Long noticeId) {
        User viewer = currentUserService.currentUser();
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResourceNotFoundException("Notice not found"));
        if (!accessService.canViewStats(viewer, notice)) {
            throw new ForbiddenException("You cannot view the history of this notice");
        }

        List<AuditLog> logs = auditLogRepository
                .findByEntityTypeAndEntityIdAndActionInOrderByTimestampAscIdAsc("Notice", noticeId, LIFECYCLE);
        Set<Long> actorIds = logs.stream().map(AuditLog::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> actors = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<TimelineEvent> events = new ArrayList<>();
        for (AuditLog log : logs) {
            User actor = log.getUserId() == null ? null : actors.get(log.getUserId());
            String name = log.getUserId() == null ? "System" : actor != null ? actor.getFullName() : log.getUserEmail();
            TimelineEvent event = new TimelineEvent(log.getAction(), name, actor == null ? null : actor.getRole(),
                    log.getTimestamp(), note(log, notice), 1);

            TimelineEvent last = events.isEmpty() ? null : events.get(events.size() - 1);
            if (last != null && last.action() == event.action() && Objects.equals(last.actorName(), event.actorName())
                    && event.action() == AuditAction.NOTICE_EDITED) {
                // Keep the latest time, count the repeats.
                events.set(events.size() - 1, new TimelineEvent(last.action(), last.actorName(), last.actorRole(),
                        event.at(), null, last.times() + 1));
            } else {
                events.add(event);
            }
        }
        return events;
    }

    private static String note(AuditLog log, Notice notice) {
        String d = log.getDescription();
        return switch (log.getAction()) {
            case NOTICE_REJECTED -> {
                if (d != null && d.contains(REASON_SEPARATOR)) {
                    yield d.substring(d.indexOf(REASON_SEPARATOR) + REASON_SEPARATOR.length());
                }
                // Entries written before the separator existed: fall back to the notice's current reason.
                yield notice.getRejectionReason();
            }
            case NOTICE_PUBLISHED -> {
                int arrow = d == null ? -1 : d.lastIndexOf(" → ");
                yield arrow < 0 ? null : "Sent to " + d.substring(arrow + 3);
            }
            case NOTICE_SCHEDULED -> notice.getPublishDate() == null ? null
                    : "Goes live " + notice.getPublishDate().format(WHEN);
            case AI_DRAFT_CREATED -> "Drafted with the AI assistant";
            default -> null;
        };
    }
}
