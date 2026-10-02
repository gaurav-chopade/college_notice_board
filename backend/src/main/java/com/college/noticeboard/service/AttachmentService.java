package com.college.noticeboard.service;

import com.college.noticeboard.service.CloudStorageService;
import com.college.noticeboard.dto.AttachmentDtos.AttachmentResponse;
import com.college.noticeboard.dto.AttachmentDtos.DownloadLinkResponse;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.dto.SimpleMappers;
import com.college.noticeboard.repository.AttachmentRepository;
import com.college.noticeboard.repository.NoticeRepository;
import com.college.noticeboard.config.CurrentUserService;
import com.college.noticeboard.service.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final int MAX_ATTACHMENTS_PER_NOTICE = 10;

    private final AttachmentRepository attachmentRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeAccessService accessService;
    private final CloudStorageService storage;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Value("${app.cloudinary.root-folder:college-notice-board/notices}")
    private String rootFolder;

    @Value("${app.cloudinary.download-url-ttl-seconds:300}")
    private long downloadTtlSeconds;

    @Value("${app.files.max-size-bytes:10485760}")
    private long maxFileBytes;

    @Transactional
    public List<AttachmentResponse> upload(Long noticeId, List<MultipartFile> files) {
        User user = currentUserService.currentUser();
        Notice notice = loadNotice(noticeId);
        if (!accessService.canEdit(user, notice)) {
            throw new ForbiddenException("You cannot add attachments to this notice");
        }
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("Select at least one file");
        }
        if (attachmentRepository.countByNoticeId(noticeId) + files.size() > MAX_ATTACHMENTS_PER_NOTICE) {
            throw new BadRequestException("A notice can have at most " + MAX_ATTACHMENTS_PER_NOTICE + " attachments");
        }
        // Validate everything before uploading anything.
        List<FileValidator.ValidatedFile> validated = files.stream().map(f -> FileValidator.validate(f, maxFileBytes)).toList();

        List<CloudStorageService.UploadResult> uploaded = new ArrayList<>();
        List<AttachmentResponse> responses = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                FileValidator.ValidatedFile vf = validated.get(i);
                String publicId = UUID.randomUUID() + (vf.image() ? "" : "." + vf.extension());
                CloudStorageService.UploadResult result = storage.upload(bytes(files.get(i)),
                        rootFolder + "/" + noticeId, publicId, vf.image());
                uploaded.add(result);
                Attachment a = attachmentRepository.save(Attachment.builder()
                        .notice(notice)
                        .originalFileName(vf.safeOriginalName())
                        .cloudinaryPublicId(result.publicId())
                        .cloudinaryUrl(result.secureUrl())
                        .resourceType(result.resourceType())
                        .fileFormat(vf.extension())
                        .fileSize(vf.size())
                        .uploadedBy(user)
                        .uploadedAt(LocalDateTime.now())
                        .build());
                auditService.log(user, AuditAction.ATTACHMENT_UPLOADED, "Attachment", a.getId(),
                        vf.safeOriginalName() + " on notice #" + noticeId);
                responses.add(SimpleMappers.toResponse(a));
            }
            attachmentRepository.flush();
        } catch (RuntimeException e) {
            // Compensate: remove files already pushed to Cloudinary so storage does not leak orphans.
            for (CloudStorageService.UploadResult r : uploaded) {
                try {
                    storage.delete(r.publicId(), r.resourceType());
                } catch (RuntimeException cleanup) {
                    log.warn("Could not clean up orphaned Cloudinary asset {}", r.publicId());
                }
            }
            throw e;
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(Long noticeId) {
        User user = currentUserService.currentUser();
        Notice notice = loadNotice(noticeId);
        accessService.assertCanView(user, notice);
        return attachmentRepository.findByNoticeIdOrderByUploadedAtAsc(noticeId).stream()
                .map(SimpleMappers::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listForAuthorisedNotice(Long noticeId) {
        return attachmentRepository.findByNoticeIdOrderByUploadedAtAsc(noticeId).stream()
                .map(SimpleMappers::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DownloadLinkResponse downloadLink(Long attachmentId) {
        User user = currentUserService.currentUser();
        Attachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        accessService.assertCanView(user, a.getNotice());
        long expiresAt = Instant.now().getEpochSecond() + downloadTtlSeconds;
        String url = storage.signedDownloadUrl(a.getCloudinaryPublicId(), a.getResourceType(), a.getFileFormat(), expiresAt);
        return new DownloadLinkResponse(url, a.getOriginalFileName(),
                LocalDateTime.ofInstant(Instant.ofEpochSecond(expiresAt), ZoneId.systemDefault()));
    }

    @Transactional
    public void delete(Long attachmentId) {
        User user = currentUserService.currentUser();
        Attachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        Notice notice = a.getNotice();
        if (!(accessService.canEdit(user, notice) || user.getRole() == Role.SUPER_ADMIN)) {
            throw new ForbiddenException("You cannot delete attachments of this notice");
        }
        // Cloud first: if it fails, keep the metadata so the file is never orphaned without a reference.
        storage.delete(a.getCloudinaryPublicId(), a.getResourceType());
        attachmentRepository.delete(a);
        auditService.log(user, AuditAction.ATTACHMENT_DELETED, "Attachment", attachmentId,
                a.getOriginalFileName() + " from notice #" + notice.getId());
    }

    /** Used when a notice is deleted. Throws if any Cloudinary delete fails so the notice is kept. */
    @Transactional
    public void deleteAllForNotice(Notice notice) {
        List<Attachment> attachments = attachmentRepository.findByNoticeIdOrderByUploadedAtAsc(notice.getId());
        for (Attachment a : attachments) {
            storage.delete(a.getCloudinaryPublicId(), a.getResourceType());
            attachmentRepository.delete(a);
        }
    }

    private Notice loadNotice(Long id) {
        return noticeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notice not found"));
    }

    private static byte[] bytes(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read uploaded file");
        }
    }
}
