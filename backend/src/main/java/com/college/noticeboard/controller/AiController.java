package com.college.noticeboard.controller;

import com.college.noticeboard.service.AiNoticeService;
import com.college.noticeboard.dto.AiDtos.AiDraftResponse;
import com.college.noticeboard.dto.AiDtos.AiNoticeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI Assistant")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiNoticeService aiNoticeService;

    @Operation(summary = "Generate a notice DRAFT from plain text (never published automatically)")
    @PostMapping("/notice-draft")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PRINCIPAL','HOD','FACULTY')")
    public AiDraftResponse draft(@Valid @RequestBody AiNoticeRequest request) {
        return aiNoticeService.generateDraft(request.text());
    }
}
