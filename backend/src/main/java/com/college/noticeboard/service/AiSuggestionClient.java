package com.college.noticeboard.service;

import com.college.noticeboard.dto.AiDtos.AiNoticeSuggestion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiSuggestionClient {

    /** Empty when the provider is not configured or the call failed; callers fall back to the mock. */
    Optional<AiNoticeSuggestion> suggest(String text, List<String> categories, LocalDateTime now);
}
