package com.college.noticeboard.service;

import com.college.noticeboard.entity.Notice;
import com.college.noticeboard.entity.NoticeTarget;
import com.college.noticeboard.entity.User;
import com.college.noticeboard.repository.UserRepository;
import com.college.noticeboard.service.NoticeSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves the exact set of active users a notice is addressed to (creator excluded). */
@Service
@RequiredArgsConstructor
public class RecipientService {

    private final UserRepository userRepository;

    public List<User> resolveRecipients(Notice notice) {
        return resolve(notice.getTargets(), notice.getCreatedBy().getId());
    }

    /**
     * Active users matching any of the targets, excluding {@code excludeUserId}. Works on unsaved
     * targets too, which is what the live recipient preview in the notice form uses.
     */
    public List<User> resolve(List<NoticeTarget> targets, Long excludeUserId) {
        // A public notice may have no targets. No targets must mean nobody, never "every user".
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        Specification<User> anyTarget = null;
        for (NoticeTarget t : targets) {
            Specification<User> s = NoticeSpecifications.usersMatching(t);
            anyTarget = anyTarget == null ? s : anyTarget.or(s);
        }
        if (anyTarget == null) {
            return List.of();
        }
        return userRepository.findAll(NoticeSpecifications.activeUsers().and(anyTarget)).stream()
                .filter(u -> !Objects.equals(u.getId(), excludeUserId))
                .toList();
    }

    public Set<Long> recipientIds(Notice notice) {
        return resolveRecipients(notice).stream().map(User::getId).collect(Collectors.toSet());
    }
}
