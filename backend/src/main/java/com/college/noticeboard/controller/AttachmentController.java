package com.college.noticeboard.controller;

import com.college.noticeboard.dto.AttachmentDtos.AttachmentResponse;
import com.college.noticeboard.dto.AttachmentDtos.DownloadLinkResponse;
import com.college.noticeboard.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Attachments")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "Upload one or more files (pdf, doc, docx, jpg, jpeg, png; max 10 MB each) to Cloudinary")
    @PostMapping(value = "/notices/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PRINCIPAL','HOD','FACULTY')")
    public List<AttachmentResponse> upload(@PathVariable Long id, @RequestPart("files") List<MultipartFile> files) {
        return attachmentService.upload(id, files);
    }

    @GetMapping("/notices/{id}/attachments")
    public List<AttachmentResponse> list(@PathVariable Long id) {
        return attachmentService.list(id);
    }

    @Operation(summary = "Get a short-lived signed download link (only if the caller can view the notice)")
    @GetMapping("/attachments/{attachmentId}/download")
    public DownloadLinkResponse download(@PathVariable Long attachmentId) {
        return attachmentService.downloadLink(attachmentId);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PRINCIPAL','HOD','FACULTY')")
    public void delete(@PathVariable Long attachmentId) {
        attachmentService.delete(attachmentId);
    }
}
