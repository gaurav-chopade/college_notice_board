package com.college.noticeboard.dto;

import com.college.noticeboard.entity.Priority;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, Long noticeId, String noticeTitle, Priority noticePriority,
                                   String message, String link, boolean read, LocalDateTime createdAt) {
}
