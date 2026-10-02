package com.college.noticeboard.service;

import com.college.noticeboard.dto.AiDtos.AiDraftResponse;
import com.college.noticeboard.dto.AiDtos.AiNoticeSuggestion;
import com.college.noticeboard.dto.NoticeDtos.NoticeDetailResponse;
import com.college.noticeboard.dto.NoticeDtos.NoticeRequest;
import com.college.noticeboard.dto.NoticeDtos.TargetRequest;
import com.college.noticeboard.entity.NoticeCategory;
import com.college.noticeboard.entity.NoticeVisibility;
import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.TargetType;
import com.college.noticeboard.entity.User;
import com.college.noticeboard.exception.ApiException;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.repository.NoticeCategoryRepository;
import com.college.noticeboard.config.CurrentUserService;
import com.college.noticeboard.service.NoticeService;
import com.college.noticeboard.service.TargetPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Turns a plain-language request into a notice DRAFT. AI output is never published automatically:
 * the creator must review it and publish/submit it through the normal workflow.
 */
@Service
@RequiredArgsConstructor
public class AiNoticeService {

    private final AnthropicAiSuggestionClient aiClient;
    private final MockAiSuggestionClient mockClient;
    private final NoticeCategoryRepository categoryRepository;
    private final TargetPolicy targetPolicy;
    private final NoticeService noticeService;
    private final CurrentUserService currentUserService;

    @Transactional
    public AiDraftResponse generateDraft(String text) {
        User user = currentUserService.currentUser();
        if (user.getRole() == Role.STUDENT) {
            throw new ForbiddenException("Students cannot create notices");
        }
        List<NoticeCategory> categories = categoryRepository.findByActiveTrueOrderByNameAsc();
        if (categories.isEmpty()) {
            throw new BadRequestException("No active categories exist. Ask the administrator to add one");
        }
        List<String> names = categories.stream().map(NoticeCategory::getName).toList();
        LocalDateTime now = LocalDateTime.now();

        Optional<AiNoticeSuggestion> fromAi = aiClient.suggest(text, names, now);
        String source = fromAi.isPresent() ? "AI" : "MOCK";
        AiNoticeSuggestion s = sanitise(fromAi.orElseGet(() -> mockClient.suggest(text, names, now).orElseThrow()), text, now);

        NoticeCategory category = categories.stream().filter(c -> c.getName().equalsIgnoreCase(s.category())).findFirst()
                .or(() -> categories.stream().filter(c -> c.getName().equalsIgnoreCase("General")).findFirst())
                .orElse(categories.get(0));

        TargetRequest suggested = new TargetRequest(s.targetType(), s.targetRole(), user.getDepartmentId(), null, null, null);
        List<TargetRequest> targets = allowed(user, suggested) ? List.of(suggested) : List.of(targetPolicy.defaultTargetFor(user));

        NoticeRequest req = new NoticeRequest(s.title(), s.description(), category.getId(), s.priority(),
                s.suggestedPublishDate(), s.suggestedExpiryDate(), false, targets, NoticeVisibility.PRIVATE, null);
        NoticeDetailResponse draft = noticeService.create(user, req, true);
        return new AiDraftResponse(source, s, draft);
    }

    private boolean allowed(User user, TargetRequest t) {
        if (t.targetType() == null || t.targetType() == TargetType.SPECIFIC_USER) {
            return false;
        }
        try {
            targetPolicy.buildTargets(user, List.of(t));
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private static AiNoticeSuggestion sanitise(AiNoticeSuggestion s, String text, LocalDateTime now) {
        String title = blank(s.title()) ? "Notice" : s.title().trim();
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }
        String desc = blank(s.description()) ? text.trim() : s.description().trim();
        LocalDateTime publish = s.suggestedPublishDate() != null && s.suggestedPublishDate().isAfter(now) ? s.suggestedPublishDate() : null;
        LocalDateTime base = publish == null ? now : publish;
        LocalDateTime expiry = s.suggestedExpiryDate() != null && s.suggestedExpiryDate().isAfter(base.plusMinutes(1))
                ? s.suggestedExpiryDate() : base.plusDays(7);
        return new AiNoticeSuggestion(title, desc, s.category(), s.priority() == null ? com.college.noticeboard.entity.Priority.NORMAL : s.priority(),
                s.targetType(), s.targetRole(), publish, expiry);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
