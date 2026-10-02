package com.college.noticeboard.service;

import com.college.noticeboard.dto.DashboardResponse;
import com.college.noticeboard.dto.NoticeDtos.NoticeStatsResponse;
import com.college.noticeboard.dto.NoticeDtos.NoticeSummaryResponse;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.repository.DepartmentRepository;
import com.college.noticeboard.repository.NoticeRepository;
import com.college.noticeboard.repository.UserRepository;
import com.college.noticeboard.config.CurrentUserService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static com.college.noticeboard.service.NoticeSpecifications.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NoticeService noticeService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public DashboardResponse admin() {
        User me = requireRole(Role.SUPER_ADMIN);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("totalUsers", userRepository.count());
        counts.put("students", userRepository.countByRole(Role.STUDENT));
        counts.put("faculty", userRepository.countByRole(Role.FACULTY));
        counts.put("hods", userRepository.countByRole(Role.HOD));
        counts.put("departments", departmentRepository.count());
        counts.put("totalNotices", noticeRepository.count());
        counts.put("activeNotices", noticeRepository.count(activeAt(LocalDateTime.now())));
        counts.put("expiredNotices", noticeRepository.countByStatus(NoticeStatus.EXPIRED));
        counts.put("scheduledNotices", noticeRepository.countByStatus(NoticeStatus.SCHEDULED));
        counts.put("pendingApproval", noticeRepository.countByStatus(NoticeStatus.PENDING_APPROVAL));
        return new DashboardResponse("SUPER_ADMIN", counts, departmentBreakdown(),
                recent(me, Specification.where(null), 6), List.of());
    }

    @Transactional(readOnly = true)
    public DashboardResponse principal() {
        User me = requireRole(Role.PRINCIPAL);
        Long id = me.getId();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("totalNotices", noticeRepository.countByCreatedById(id));
        counts.put("published", noticeRepository.countByCreatedByIdAndStatus(id, NoticeStatus.PUBLISHED));
        counts.put("scheduled", noticeRepository.countByCreatedByIdAndStatus(id, NoticeStatus.SCHEDULED));
        counts.put("drafts", noticeRepository.countByCreatedByIdAndStatus(id, NoticeStatus.DRAFT));
        counts.put("important", noticeRepository.countByCreatedByIdAndPriority(id, Priority.IMPORTANT));
        counts.put("urgent", noticeRepository.countByCreatedByIdAndPriority(id, Priority.URGENT));
        counts.put("collegeNoticesActive", noticeRepository.count(activeAt(LocalDateTime.now())));
        return new DashboardResponse("PRINCIPAL", counts, departmentBreakdown(),
                recent(me, createdBy(id), 6), readStats(createdBy(id)));
    }

    @Transactional(readOnly = true)
    public DashboardResponse hod() {
        User me = requireRole(Role.HOD);
        Long dept = me.getDepartmentId();
        Map<String, Long> counts = new LinkedHashMap<>();
        if (dept == null) {
            return new DashboardResponse("HOD", counts, Map.of(), List.of(), List.of());
        }
        Specification<Notice> facultyOfDept = createdByRole(Role.FACULTY).and(inDepartment(dept));
        counts.put("departmentNotices", noticeRepository.countByDepartmentId(dept));
        counts.put("pendingApprovals", noticeRepository.count(facultyOfDept.and(status(NoticeStatus.PENDING_APPROVAL))));
        counts.put("published", noticeRepository.countByDepartmentIdAndStatus(dept, NoticeStatus.PUBLISHED));
        counts.put("rejected", noticeRepository.count(facultyOfDept.and(status(NoticeStatus.REJECTED))));
        counts.put("myNotices", noticeRepository.countByCreatedById(me.getId()));
        counts.put("forMe", noticeRepository.count(activeAt(LocalDateTime.now()).and(targetedTo(me))));
        Specification<Notice> mineOrFaculty = createdBy(me.getId()).or(facultyOfDept);
        return new DashboardResponse("HOD", counts, Map.of(),
                recent(me, facultyOfDept.and(status(NoticeStatus.PENDING_APPROVAL)), 6),
                readStats(mineOrFaculty));
    }

    @Transactional(readOnly = true)
    public DashboardResponse faculty() {
        User me = requireRole(Role.FACULTY);
        Long id = me.getId();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("myNotices", noticeRepository.countByCreatedById(id));
        counts.put("drafts", noticeRepository.countByCreatedByIdAndStatus(id, NoticeStatus.DRAFT));
        counts.put("pendingApproval", noticeRepository.countByCreatedByIdAndStatus(id, NoticeStatus.PENDING_APPROVAL));
        counts.put("approved", noticeRepository.countByCreatedByIdAndReviewedByIsNotNullAndStatusNot(id, NoticeStatus.REJECTED));
        counts.put("rejected", noticeRepository.countByCreatedByIdAndStatus(id, NoticeStatus.REJECTED));
        counts.put("forMe", noticeRepository.count(activeAt(LocalDateTime.now()).and(targetedTo(me))));
        return new DashboardResponse("FACULTY", counts, Map.of(), recent(me, createdBy(id), 6), readStats(createdBy(id)));
    }

    @Transactional(readOnly = true)
    public DashboardResponse student() {
        User me = requireRole(Role.STUDENT);
        Specification<Notice> feed = activeAt(LocalDateTime.now()).and(targetedTo(me));
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("latest", noticeRepository.count(feed));
        counts.put("unread", noticeRepository.count(feed.and(notReadBy(me.getId()))));
        counts.put("important", noticeRepository.count(feed.and((r, q, cb) -> r.get("priority").in(Priority.IMPORTANT, Priority.URGENT))));
        counts.put("examination", noticeRepository.count(feed.and(categoryNamed("Examination"))));
        counts.put("placement", noticeRepository.count(feed.and(categoryNamed("Placement"))));
        counts.put("events", noticeRepository.count(feed.and(categoryNamed("Event"))));
        return new DashboardResponse("STUDENT", counts, Map.of(), recent(me, feed, 6), List.of());
    }

    private static Specification<Notice> categoryNamed(String name) {
        return (r, q, cb) -> cb.equal(cb.lower(r.join("category", JoinType.INNER).get("name")), name.toLowerCase(Locale.ROOT));
    }

    private List<NoticeSummaryResponse> recent(User me, Specification<Notice> spec, int n) {
        var page = noticeRepository.findAll(spec, PageRequest.of(0, n, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return noticeService.summaries(me, page.getContent());
    }

    private List<NoticeStatsResponse> readStats(Specification<Notice> base) {
        var page = noticeRepository.findAll(base.and(statusIn(List.of(NoticeStatus.PUBLISHED, NoticeStatus.EXPIRED))),
                PageRequest.of(0, 5, Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))));
        return page.getContent().stream().map(noticeService::statsFor).toList();
    }

    private Map<String, Long> departmentBreakdown() {
        Map<String, Long> map = new TreeMap<>();
        for (Object[] row : noticeRepository.countGroupedByDepartment()) {
            map.put((String) row[0], (Long) row[1]);
        }
        return map;
    }

    private User requireRole(Role role) {
        User me = currentUserService.currentUser();
        if (me.getRole() != role) {
            throw new ForbiddenException("This dashboard is for " + role + " users");
        }
        return me;
    }
}
