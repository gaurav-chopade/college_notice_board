package com.college.noticeboard.controller;

import com.college.noticeboard.dto.MessageResponse;
import com.college.noticeboard.dto.NotificationResponse;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.service.NotificationService;
import com.college.noticeboard.config.CurrentUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return notificationService.list(currentUserService.currentUserId(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(currentUserService.currentUserId()));
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(currentUserService.currentUserId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        notificationService.delete(currentUserService.currentUserId(), id);
    }

    @PutMapping("/read-all")
    public MessageResponse markAllRead() {
        int n = notificationService.markAllRead(currentUserService.currentUserId());
        return MessageResponse.ok(n + " notification(s) marked as read");
    }
}
