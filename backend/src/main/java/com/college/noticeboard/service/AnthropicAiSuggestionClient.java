package com.college.noticeboard.service;

import com.college.noticeboard.dto.AiDtos.AiNoticeSuggestion;
import com.college.noticeboard.entity.Priority;
import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.TargetType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Calls the Anthropic Messages API when AI_API_KEY is set. Any failure returns empty (mock is used instead). */
@Slf4j
@Component
public class AnthropicAiSuggestionClient implements AiSuggestionClient {

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AnthropicAiSuggestionClient(@Value("${app.ai.api-key:}") String apiKey,
                                       @Value("${app.ai.api-url}") String apiUrl,
                                       @Value("${app.ai.model}") String model,
                                       ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Optional<AiNoticeSuggestion> suggest(String text, List<String> categories, LocalDateTime now) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String system = """
                You write official college notices. Reply with ONLY a JSON object, no prose, no code fences:
                {"title": string (max 120 chars), "description": string (formal notice body),
                 "category": one of %s,
                 "priority": "NORMAL" | "IMPORTANT" | "URGENT",
                 "targetType": "COLLEGE" | "ROLE",
                 "targetRole": null | "HOD" | "FACULTY" | "STUDENT",
                 "suggestedPublishDate": ISO-8601 local date-time or null,
                 "suggestedExpiryDate": ISO-8601 local date-time}
                The current local date-time is %s.
                """.formatted(categories, now.withNano(0));
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 1000,
                    "system", system,
                    "messages", List.of(Map.of("role", "user", "content", text)));
            String raw = restClient.post().uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText());
                }
            }
            String json = sb.toString().replaceAll("(?s)```json|```", "").trim();
            JsonNode s = objectMapper.readTree(json);
            return Optional.of(new AiNoticeSuggestion(
                    s.path("title").asText(null),
                    s.path("description").asText(null),
                    s.path("category").asText("General"),
                    enumOr(Priority.class, s.path("priority").asText(null), Priority.NORMAL),
                    enumOr(TargetType.class, s.path("targetType").asText(null), TargetType.COLLEGE),
                    enumOr(Role.class, s.path("targetRole").asText(null), null),
                    date(s.path("suggestedPublishDate").asText(null)),
                    date(s.path("suggestedExpiryDate").asText(null))));
        } catch (Exception e) {
            log.warn("AI provider call failed, falling back to mock: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static LocalDateTime date(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            String v = value.trim();
            return v.length() == 10 ? LocalDateTime.parse(v + "T09:00:00") : LocalDateTime.parse(v.replace("Z", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
