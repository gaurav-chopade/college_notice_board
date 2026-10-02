package com.college.noticeboard.service;

import com.college.noticeboard.entity.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class NoticeSpecifications {

    private NoticeSpecifications() {
    }

    /** EXISTS a NoticeTarget row on the notice that matches the user (same rule as {@link TargetMatcher}). */
    public static Specification<Notice> targetedTo(User u) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<NoticeTarget> t = sq.from(NoticeTarget.class);
            Join<NoticeTarget, Department> td = t.join("department", JoinType.LEFT);
            Join<NoticeTarget, User> tu = t.join("user", JoinType.LEFT);

            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(t.get("notice"), root));
            p.add(cb.or(cb.isNull(t.get("targetRole")), cb.equal(t.get("targetRole"), u.getRole())));
            p.add(u.getDepartmentId() == null
                    ? cb.isNull(td.get("id"))
                    : cb.or(cb.isNull(td.get("id")), cb.equal(td.get("id"), u.getDepartmentId())));
            p.add(u.getSemester() == null
                    ? cb.isNull(t.get("semester"))
                    : cb.or(cb.isNull(t.get("semester")), cb.equal(t.get("semester"), u.getSemester())));
            p.add(u.getClassName() == null
                    ? cb.isNull(t.get("className"))
                    : cb.or(cb.isNull(t.get("className")), cb.equal(cb.upper(t.get("className")), u.getClassName().toUpperCase())));
            p.add(cb.or(cb.isNull(tu.get("id")), cb.equal(tu.get("id"), u.getId())));

            sq.select(t.get("id")).where(p.toArray(new Predicate[0]));
            return cb.exists(sq);
        };
    }

    /** Users included by a single target row; OR these together to resolve all recipients. */
    public static Specification<User> usersMatching(NoticeTarget t) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (t.getTargetRole() != null) {
                p.add(cb.equal(root.get("role"), t.getTargetRole()));
            }
            if (t.getDepartment() != null) {
                p.add(cb.equal(root.join("department", JoinType.LEFT).get("id"), t.getDepartment().getId()));
            }
            if (t.getSemester() != null) {
                p.add(cb.equal(root.get("semester"), t.getSemester()));
            }
            if (t.getClassName() != null) {
                p.add(cb.equal(cb.upper(root.get("className")), t.getClassName().toUpperCase()));
            }
            if (t.getUser() != null) {
                p.add(cb.equal(root.get("id"), t.getUser().getId()));
            }
            return p.isEmpty() ? cb.conjunction() : cb.and(p.toArray(new Predicate[0]));
        };
    }

    public static Specification<User> activeUsers() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Notice> statusIn(Collection<NoticeStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Notice> status(NoticeStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Published, publish time reached and not yet expired. */
    public static Specification<Notice> activeAt(LocalDateTime now) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), NoticeStatus.PUBLISHED),
                cb.or(cb.isNull(root.get("publishDate")), cb.lessThanOrEqualTo(root.get("publishDate"), now)),
                cb.or(cb.isNull(root.get("expiryDate")), cb.greaterThan(root.get("expiryDate"), now)));
    }

    public static Specification<Notice> createdBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), userId);
    }

    public static Specification<Notice> createdByRole(Role role) {
        return (root, query, cb) -> cb.equal(root.join("createdBy").get("role"), role);
    }

    public static Specification<Notice> inDepartment(Long departmentId) {
        return (root, query, cb) -> cb.equal(root.join("department", JoinType.LEFT).get("id"), departmentId);
    }

    public static Specification<Notice> textSearch(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String like = "%" + q.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like, '\\'),
                cb.like(cb.lower(root.get("description")), like, '\\'));
    }

    public static Specification<Notice> category(Long categoryId) {
        return categoryId == null ? null : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Notice> priority(Priority priority) {
        return priority == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    /** Filter by the department a notice belongs to, or that one of its targets addresses. */
    public static Specification<Notice> departmentFilter(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<NoticeTarget> t = sq.from(NoticeTarget.class);
            sq.select(t.get("id")).where(cb.equal(t.get("notice"), root),
                    cb.equal(t.join("department", JoinType.LEFT).get("id"), departmentId));
            return cb.or(cb.equal(root.join("department", JoinType.LEFT).get("id"), departmentId), cb.exists(sq));
        };
    }

    public static Specification<Notice> semesterFilter(Integer semester) {
        if (semester == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<NoticeTarget> t = sq.from(NoticeTarget.class);
            sq.select(t.get("id")).where(cb.equal(t.get("notice"), root), cb.equal(t.get("semester"), semester));
            return cb.exists(sq);
        };
    }

    public static Specification<Notice> creator(Long creatorId) {
        return creatorId == null ? null : createdBy(creatorId);
    }

    public static Specification<Notice> createdBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /**
     * Public and live right now: the single rule for what anonymous visitors may see.
     * Mirrors {@link Notice#isPubliclyVisible(LocalDateTime)}; keep the two in step.
     */
    public static Specification<Notice> publiclyVisible(LocalDateTime now) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("visibility"), NoticeVisibility.PUBLIC),
                cb.equal(root.get("status"), NoticeStatus.PUBLISHED),
                cb.or(cb.isNull(root.get("publishDate")), cb.lessThanOrEqualTo(root.get("publishDate"), now)),
                cb.or(cb.isNull(root.get("expiryDate")), cb.greaterThan(root.get("expiryDate"), now)));
    }

    /** Public notices for one wing, including those marked for both. */
    public static Specification<Notice> forInstitution(InstitutionType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> root.get("institutionScope").in(InstitutionScope.of(type), InstitutionScope.BOTH);
    }

    public static Specification<Notice> publishedBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("publishDate"), from.atStartOfDay()));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("publishDate"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /** Notices the user has saved to their reading list. */
    public static Specification<Notice> savedBy(Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<SavedNotice> s = sq.from(SavedNotice.class);
            sq.select(s.get("id")).where(cb.equal(s.get("notice"), root), cb.equal(s.get("user").get("id"), userId));
            return cb.exists(sq);
        };
    }

    public static Specification<Notice> notReadBy(Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<NoticeRead> r = sq.from(NoticeRead.class);
            sq.select(r.get("id")).where(cb.equal(r.get("notice"), root), cb.equal(r.get("user").get("id"), userId));
            return cb.not(cb.exists(sq));
        };
    }
}
