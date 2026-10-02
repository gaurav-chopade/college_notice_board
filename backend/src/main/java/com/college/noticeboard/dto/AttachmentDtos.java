package com.college.noticeboard.dto;

import java.time.LocalDateTime;

public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    /** The Cloudinary URL is intentionally not exposed; use the download endpoint for a short-lived signed link. */
    public record AttachmentResponse(Long id, Long noticeId, String originalFileName, String resourceType,
                                     String fileFormat, Long fileSize, String uploadedByName, LocalDateTime uploadedAt) {
    }

    public record DownloadLinkResponse(String url, String fileName, LocalDateTime expiresAt) {
    }
}
