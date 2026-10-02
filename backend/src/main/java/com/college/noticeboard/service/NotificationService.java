package com.college.noticeboard.service;

import com.college.noticeboard.dto.NotificationResponse;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.dto.SimpleMappers;
import com.college.noticeboard.repository.NotificationRepository;
import com.college.noticeboard.repository.UserRepository;
import com.college.noticeboard.service.RecipientService;
import com.college.noticeboard.service.NoticeSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RecipientService recipientService;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** In-app notifications for exactly the target audience; email only for IMPORTANT/URGENT. Returns recipient count. */
    @Transactional
    public int notifyPublished(Notice notice) {
        List<User> recipients = recipientService.resolveRecipients(notice);
        String prefix = notice.getPriority() == Priority.NORMAL ? "New notice" : notice.getPriority().name() + " notice";
        String message = prefix + ": " + notice.getTitle();
        LocalDateTime now = LocalDateTime.now();
        notificationRepository.saveAll(recipients.stream()
                .map(u -> Notification.builder().user(u).notice(notice).message(trim(message)).createdAt(now).build())
                .toList());

        if (notice.getPriority() != Priority.NORMAL && !recipients.isEmpty()) {
            String body = notice.getTitle() + "\n\n" + notice.getDescription()
                    + "\n\nView it on the notice board: " + frontendUrl + "/notices/" + notice.getId();
            emailService.sendToEach(recipients.stream().map(User::getEmail).toList(),
                    "[" + notice.getPriority().name() + "] " + notice.getTitle(), body);
        }
        return recipients.size();
    }

    @Transactional
    public void notifyUser(User user, Notice notice, String message) {
        notificationRepository.save(Notification.builder().user(user).notice(notice).message(trim(message))
                .createdAt(LocalDateTime.now()).build());
    }

    /** A notification that is not about a notice, opening {@code link} in the app. */
    @Transactional
    public void notifyLink(User user, String message, String link) {
        notificationRepository.save(Notification.builder().user(user).message(trim(message)).link(link)
                .createdAt(LocalDateTime.now()).build());
    }

    /** Tells the HODs of the faculty member's department that a notice is waiting for approval. */
    @Transactional
    public void notifyHodsOfSubmission(Notice notice) {
        if (notice.getDepartment() == null) {
            return;
        }
        NoticeTarget hodsOfDept = NoticeTarget.builder().targetType(TargetType.ROLE_DEPARTMENT)
                .targetRole(Role.HOD).department(notice.getDepartment()).build();
        userRepository.findAll(NoticeSpecifications.activeUsers().and(NoticeSpecifications.usersMatching(hodsOfDept)))
                .forEach(hod -> notifyUser(hod, notice,
                        "Approval needed: " + notice.getTitle() + " by " + notice.getCreatedBy().getFullName()));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        var result = unreadOnly
                ? notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(result, SimpleMappers::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .filter(x -> Objects.equals(x.getUser().getId(), userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        n.setRead(true);
        return SimpleMappers.toResponse(n);
    }

    /** Users can only delete their own notifications; anyone else's look like they do not exist. */
    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .filter(x -> Objects.equals(x.getUser().getId(), userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notificationRepository.delete(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }

    private static String trim(String s) {
        return s.length() > 500 ? s.substring(0, 497) + "..." : s;
    }
}
