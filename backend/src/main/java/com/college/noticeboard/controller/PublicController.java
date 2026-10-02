package com.college.noticeboard.controller;

import com.college.noticeboard.dto.AttachmentDtos.DownloadLinkResponse;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.dto.PublicDtos.*;
import com.college.noticeboard.entity.InstitutionType;
import com.college.noticeboard.entity.Priority;
import com.college.noticeboard.service.PublicNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** No sign-in needed. Only notices that are public, published and not expired are ever returned. */
@Tag(name = "Public site")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicNoticeService publicNoticeService;

    @GetMapping("/college")
    public CollegeInfoResponse college() {
        return publicNoticeService.college();
    }

    @Operation(summary = "Engineering and Polytechnic with their departments and programmes")
    @GetMapping("/institutions")
    public List<InstitutionInfo> institutions() {
        return publicNoticeService.institutions();
    }

    @GetMapping("/categories")
    public List<PublicCategory> categories() {
        return publicNoticeService.categories();
    }

    @Operation(summary = "Public notices that are live now",
            description = "institution=ENGINEERING also includes notices for both wings; same for POLYTECHNIC.")
    @GetMapping("/notices")
    public PageResponse<PublicNoticeSummary> notices(
            @RequestParam(required = false) InstitutionType institution,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return publicNoticeService.list(institution, categoryId, q, priority, fromDate, toDate, page, size);
    }

    @Operation(summary = "One public notice; 404 for anything that is not publicly visible right now")
    @GetMapping("/notices/{id}")
    public PublicNoticeDetail notice(@PathVariable Long id) {
        return publicNoticeService.get(id);
    }

    @Operation(summary = "Short-lived download link for an attachment of a public notice")
    @GetMapping("/notices/{id}/attachments/{attachmentId}/link")
    public DownloadLinkResponse attachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return publicNoticeService.attachmentLink(id, attachmentId);
    }
}
