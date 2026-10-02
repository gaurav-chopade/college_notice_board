package com.college.noticeboard.service;

import com.college.noticeboard.entity.NoticeTarget;
import com.college.noticeboard.entity.User;

import java.util.Collection;
import java.util.Objects;

/**
 * Single source of truth for "does this target row include this user?".
 * A row matches when every non-null field equals the user's value. The same rule is
 * expressed as SQL in {@link NoticeSpecifications#targetedTo} and {@link NoticeSpecifications#usersMatching}.
 */
public final class TargetMatcher {

    private TargetMatcher() {
    }

    public static boolean matches(NoticeTarget t, User u) {
        if (t.getTargetRole() != null && t.getTargetRole() != u.getRole()) {
            return false;
        }
        if (t.getDepartment() != null
                && (u.getDepartment() == null || !Objects.equals(t.getDepartment().getId(), u.getDepartment().getId()))) {
            return false;
        }
        if (t.getSemester() != null && !Objects.equals(t.getSemester(), u.getSemester())) {
            return false;
        }
        if (t.getClassName() != null && (u.getClassName() == null || !t.getClassName().equalsIgnoreCase(u.getClassName()))) {
            return false;
        }
        return t.getUser() == null || Objects.equals(t.getUser().getId(), u.getId());
    }

    public static boolean matchesAny(Collection<NoticeTarget> targets, User u) {
        return targets.stream().anyMatch(t -> matches(t, u));
    }
}
