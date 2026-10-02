package com.college.noticeboard.service;

import com.college.noticeboard.dto.AiDtos.AiNoticeSuggestion;
import com.college.noticeboard.entity.Priority;
import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.TargetType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/** Offline, rule-based fallback so the assistant works without any AI API key. */
@Component
public class MockAiSuggestionClient implements AiSuggestionClient {

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("Holiday", List.of("holiday", "closed", "vacation", "leave", "festival"));
        KEYWORDS.put("Examination", List.of("exam", "examination", "test", "hall ticket", "result", "marks"));
        KEYWORDS.put("Placement", List.of("placement", "recruit", "interview", "company", "drive", "internship"));
        KEYWORDS.put("Scholarship", List.of("scholarship", "stipend", "financial aid"));
        KEYWORDS.put("Fee", List.of("fee", "fees", "payment", "dues"));
        KEYWORDS.put("Workshop", List.of("workshop", "hands-on", "bootcamp"));
        KEYWORDS.put("Seminar", List.of("seminar", "guest lecture", "talk", "webinar"));
        KEYWORDS.put("Sports", List.of("sports", "tournament", "match", "athletic"));
        KEYWORDS.put("Cultural", List.of("cultural", "fest", "dance", "music", "drama"));
        KEYWORDS.put("Event", List.of("event", "celebration", "function", "ceremony"));
        KEYWORDS.put("Admission", List.of("admission", "enrolment", "enrollment"));
        KEYWORDS.put("Library", List.of("library", "book", "journal"));
        KEYWORDS.put("Academic", List.of("class", "lecture", "timetable", "syllabus", "assignment", "semester"));
    }

    @Override
    public Optional<AiNoticeSuggestion> suggest(String text, List<String> categories, LocalDateTime now) {
        String lower = text.toLowerCase(Locale.ROOT);
        String category = KEYWORDS.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(lower::contains))
                .map(Map.Entry::getKey)
                .filter(c -> categories.stream().anyMatch(x -> x.equalsIgnoreCase(c)))
                .findFirst().orElse("General");

        Priority priority = lower.matches(".*\\b(urgent|immediately|emergency|today)\\b.*") ? Priority.URGENT
                : lower.matches(".*\\b(important|mandatory|compulsory|tomorrow|deadline|closed)\\b.*") ? Priority.IMPORTANT
                : Priority.NORMAL;

        TargetType type = TargetType.COLLEGE;
        Role role = null;
        if (lower.contains("hod")) {
            type = TargetType.ROLE;
            role = Role.HOD;
        } else if (lower.contains("faculty") || lower.contains("teachers") || lower.contains("staff")) {
            type = TargetType.ROLE;
            role = Role.FACULTY;
        } else if (lower.contains("students")) {
            type = TargetType.ROLE;
            role = Role.STUDENT;
        }

        LocalDateTime publish = now.plusMinutes(5).withSecond(0).withNano(0);
        LocalDateTime expiry = lower.contains("tomorrow")
                ? now.toLocalDate().plusDays(2).atTime(LocalTime.MIDNIGHT)
                : publish.plusDays(7);

        String title = buildTitle(text, category);
        String description = "Dear all,\n\n" + capitalise(text.trim()) + (text.trim().endsWith(".") ? "" : ".")
                + "\n\nPlease take note.\n\nRegards,\nCollege Administration";
        return Optional.of(new AiNoticeSuggestion(title, description, category, priority, type, role, publish, expiry));
    }

    private static String buildTitle(String text, String category) {
        String firstSentence = text.trim().split("[.!?\\n]")[0];
        String words = String.join(" ", Arrays.stream(firstSentence.split("\\s+")).limit(8).toList());
        String title = capitalise(words);
        if (title.length() < 6) {
            title = category + " notice";
        }
        return title.length() > 120 ? title.substring(0, 120) : title;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
