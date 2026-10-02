package com.college.noticeboard.controller;

import com.college.noticeboard.dto.MessageResponse;
import com.college.noticeboard.dto.NoticeDtos.*;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.dto.TargetOptionsResponse;
import com.college.noticeboard.service.NoticeService;
import com.college.noticeboard.service.NoticeTimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notices")
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private static final String CREATORS = "hasAnyRole('SUPER_ADMIN','PRINCIPAL','HOD','FACULTY')";

    private final NoticeService noticeService;
    private final NoticeTimelineService timelineService;

    @Operation(summary = "List notices the caller is authorised to see",
            description = "scope: feed (active, targeted to me), history (published+expired targeted to me), "
                    + "mine (created by me), approvals (HOD: faculty notices of my department), "
                    + "saved (my reading list), all (super admin)")
    @GetMapping
    public PageResponse<NoticeSummaryResponse> list(@ParameterObject NoticeFilter filter,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return noticeService.list(filter, page, size);
    }

    @Operation(summary = "Targeting options allowed for the caller's role")
    @GetMapping("/target-options")
    @PreAuthorize(CREATORS)
    public TargetOptionsResponse targetOptions() {
        return noticeService.targetOptions();
    }

    @Operation(summary = "Count who an audience would reach, before saving",
            description = "Validates the targets exactly as on save (403/400 for audiences the caller may not use).")
    @PostMapping("/recipient-preview")
    @PreAuthorize(CREATORS)
    public RecipientPreviewResponse recipientPreview(@Valid @RequestBody RecipientPreviewRequest request) {
        return noticeService.previewRecipients(request);
    }

    @GetMapping("/{id}")
    public NoticeDetailResponse get(@PathVariable Long id) {
        return noticeService.get(id);
    }

    @Operation(summary = "Create a notice (always saved as DRAFT)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CREATORS)
    public NoticeDetailResponse create(@Valid @RequestBody NoticeRequest request) {
        return noticeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CREATORS)
    public NoticeDetailResponse update(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        return noticeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CREATORS)
    public void delete(@PathVariable Long id) {
        noticeService.delete(id);
    }

    @Operation(summary = "Publish now, or schedule if the publish date is in the future (Principal/HOD/Admin)")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PRINCIPAL','HOD')")
    public NoticeDetailResponse publish(@PathVariable Long id) {
        return noticeService.publish(id);
    }

    @Operation(summary = "Faculty: submit a draft for HOD approval")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('FACULTY')")
    public NoticeDetailResponse submit(@PathVariable Long id) {
        return noticeService.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('HOD')")
    public NoticeDetailResponse approve(@PathVariable Long id) {
        return noticeService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('HOD')")
    public NoticeDetailResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return noticeService.reject(id, request.reason());
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(CREATORS)
    public NoticeDetailResponse archive(@PathVariable Long id) {
        return noticeService.archive(id);
    }

    @Operation(summary = "Recipient: 'I have read this notice'")
    @PostMapping("/{id}/acknowledge")
    public NoticeDetailResponse acknowledge(@PathVariable Long id) {
        return noticeService.acknowledge(id);
    }

    @Operation(summary = "Save a notice to my reading list")
    @PutMapping("/{id}/save")
    public NoticeDetailResponse save(@PathVariable Long id) {
        return noticeService.save(id);
    }

    @Operation(summary = "Remove a notice from my reading list")
    @DeleteMapping("/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@PathVariable Long id) {
        noticeService.unsave(id);
    }

    @PostMapping("/{id}/read")
    public MessageResponse markRead(@PathVariable Long id) {
        noticeService.markRead(id);
        return MessageResponse.ok("Marked as read");
    }

    @Operation(summary = "Read/acknowledgement statistics counted over intended recipients only")
    @GetMapping("/{id}/stats")
    @PreAuthorize(CREATORS)
    public NoticeStatsResponse stats(@PathVariable Long id) {
        return noticeService.stats(id);
    }

    @Operation(summary = "Lifecycle timeline (creator, reviewing HOD and super admin only)")
    @GetMapping("/{id}/timeline")
    @PreAuthorize(CREATORS)
    public List<TimelineEvent> timeline(@PathVariable Long id) {
        return timelineService.timeline(id);
    }
}
