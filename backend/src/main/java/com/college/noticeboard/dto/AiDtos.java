package com.college.noticeboard.dto;

import com.college.noticeboard.entity.Priority;
import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AiDtos {

    private AiDtos() {
    }

    public record AiNoticeRequest(@NotBlank @Size(min = 5, max = 4000) String text) {
    }

    public record AiNoticeSuggestion(String title, String description, String category, Priority priority,
                                     TargetType targetType, Role targetRole,
                                     LocalDateTime suggestedPublishDate, LocalDateTime suggestedExpiryDate) {
    }

    /** source is "AI" when the external model answered, "MOCK" when the offline fallback was used. */
    public record AiDraftResponse(String source, AiNoticeSuggestion suggestion, NoticeDtos.NoticeDetailResponse draft) {
    }
}
