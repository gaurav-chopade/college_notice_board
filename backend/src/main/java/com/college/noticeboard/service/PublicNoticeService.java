package com.college.noticeboard.service;

import com.college.noticeboard.config.CollegeInfo;
import com.college.noticeboard.dto.AttachmentDtos.DownloadLinkResponse;
import com.college.noticeboard.dto.NoticeMapper;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.dto.PublicDtos.*;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.repository.AttachmentRepository;
import com.college.noticeboard.repository.DepartmentRepository;
import com.college.noticeboard.repository.NoticeCategoryRepository;
import com.college.noticeboard.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.college.noticeboard.service.NoticeSpecifications.*;

/**
 * Everything anonymous visitors can read. Every query starts from {@link NoticeSpecifications#publiclyVisible}
 * and every single-item lookup re-checks {@link Notice#isPubliclyVisible}, so a private, draft, pending,
 * rejected, scheduled, expired or archived notice answers 404 here, exactly like an id that does not exist.
 */
@Service
@RequiredArgsConstructor
public class PublicNoticeService {

    private final NoticeRepository noticeRepository;
    private final AttachmentRepository attachmentRepository;
    private final NoticeCategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final CloudStorageService storage;
    private final CollegeInfo college;

    @Value("${app.cloudinary.download-url-ttl-seconds:300}")
    private long downloadTtlSeconds;

    public CollegeInfoResponse college() {
        return new CollegeInfoResponse(college.getName(), college.getTagline(), college.getAbout(), college.getAddress(),
                college.getPhone(), college.getEmail(), college.getAdmissionPhone(), college.getOfficeHours());
    }

    /** Both wings with their departments and programmes, straight from the department table. */
    @Transactional(readOnly = true)
    public List<InstitutionInfo> institutions() {
        Map<InstitutionType, List<Department>> byType = departmentRepository.findAll(Sort.by("name")).stream()
                .collect(Collectors.groupingBy(Department::getInstitutionType, () -> new EnumMap<>(InstitutionType.class),
                        Collectors.toList()));
        List<InstitutionInfo> out = new ArrayList<>();
        for (InstitutionType t : InstitutionType.values()) {
            List<Department> depts = byType.getOrDefault(t, List.of());
            List<String> programs = depts.stream().map(Department::getProgramName).distinct().toList();
            out.add(new InstitutionInfo(t, t == InstitutionType.ENGINEERING ? "Engineering" : "Polytechnic",
                    t == InstitutionType.ENGINEERING ? college.getEngineeringAbout() : college.getPolytechnicAbout(),
                    programs,
                    depts.stream().map(d -> new PublicDepartment(d.getName(), d.getCode(), d.getProgramName(),
                            d.getSemesterCount(), d.getDescription())).toList()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<PublicCategory> categories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> new PublicCategory(c.getId(), c.getName())).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicNoticeSummary> list(InstitutionType institution, Long categoryId, String q, Priority priority,
                                                  LocalDate from, LocalDate to, int page, int size) {
        Specification<Notice> spec = publiclyVisible(LocalDateTime.now())
                .and(forInstitution(institution)).and(textSearch(q)).and(category(categoryId))
                .and(priority(priority)).and(publishedBetween(from, to));
        Page<Notice> result = noticeRepository.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("publishDate"), Sort.Order.desc("id"))));
        List<Long> ids = result.getContent().stream().map(Notice::getId).toList();
        Map<Long, Long> counts = ids.isEmpty() ? Map.of() : attachmentRepository.countByNoticeIds(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return PageResponse.of(result, result.getContent().stream().map(n -> new PublicNoticeSummary(n.getId(), n.getTitle(),
                NoticeMapper.excerpt(n.getDescription()), n.getInstitutionScope(), n.getCategory().getId(),
                n.getCategory().getName(), n.getPriority(), n.getPublishDate(), n.getExpiryDate(),
                counts.getOrDefault(n.getId(), 0L))).toList());
    }

    @Transactional(readOnly = true)
    public PublicNoticeDetail get(Long id) {
        Notice n = visible(id);
        List<PublicAttachment> attachments = attachmentRepository.findByNoticeIdOrderByUploadedAtAsc(id).stream()
                .map(a -> new PublicAttachment(a.getId(), a.getOriginalFileName(), a.getFileFormat(), a.getFileSize(),
                        "image".equals(a.getResourceType()))).toList();
        return new PublicNoticeDetail(n.getId(), n.getTitle(), n.getDescription(), n.getInstitutionScope(),
                n.getCategory().getName(), n.getPriority(), n.getPublishDate(), n.getExpiryDate(), issuedBy(n),
                attachments, blankToNull(college.getAdmissionPhone() == null || college.getAdmissionPhone().isBlank()
                        ? college.getPhone() : college.getAdmissionPhone()), blankToNull(college.getEmail()));
    }

    /** Short-lived link, only for an attachment that belongs to a notice that is publicly visible right now. */
    @Transactional(readOnly = true)
    public DownloadLinkResponse attachmentLink(Long noticeId, Long attachmentId) {
        visible(noticeId);
        Attachment a = attachmentRepository.findById(attachmentId)
                .filter(x -> Objects.equals(x.getNotice().getId(), noticeId))
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        long expiresAt = Instant.now().getEpochSecond() + downloadTtlSeconds;
        String url = storage.signedDownloadUrl(a.getCloudinaryPublicId(), a.getResourceType(), a.getFileFormat(), expiresAt);
        return new DownloadLinkResponse(url, a.getOriginalFileName(),
                LocalDateTime.ofInstant(Instant.ofEpochSecond(expiresAt), ZoneId.systemDefault()));
    }

    private Notice visible(Long id) {
        return noticeRepository.findById(id)
                .filter(n -> n.isPubliclyVisible(LocalDateTime.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Notice not found"));
    }

    private static String issuedBy(Notice n) {
        return switch (n.getCreatedBy().getRole()) {
            case PRINCIPAL -> "Office of the Principal";
            case HOD, FACULTY -> n.getDepartment() == null ? "College office" : "Department of " + n.getDepartment().getName();
            default -> "College administration";
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
