package com.college.noticeboard.dto;

import com.college.noticeboard.entity.InstitutionScope;
import com.college.noticeboard.entity.InstitutionType;
import com.college.noticeboard.entity.Priority;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the public site may see. Deliberately separate from the member DTOs so internal fields
 * (creator ids, targets, approval history, status, rejection reasons) cannot leak by accident.
 */
public final class PublicDtos {

    private PublicDtos() {
    }

    public record CollegeInfoResponse(String name, String tagline, String about, String address, String phone,
                                      String email, String admissionPhone, String officeHours) {
    }

    public record PublicDepartment(String name, String code, String programName, int semesterCount, String description) {
    }

    public record InstitutionInfo(InstitutionType type, String label, String about, List<String> programs,
                                  List<PublicDepartment> departments) {
    }

    public record PublicCategory(Long id, String name) {
    }

    public record PublicNoticeSummary(Long id, String title, String excerpt, InstitutionScope institutionScope,
                                      Long categoryId, String categoryName, Priority priority,
                                      LocalDateTime publishDate, LocalDateTime expiryDate, long attachmentCount) {
    }

    public record PublicAttachment(Long id, String fileName, String fileFormat, Long fileSize, boolean image) {
    }

    public record PublicNoticeDetail(Long id, String title, String description, InstitutionScope institutionScope,
                                     String categoryName, Priority priority, LocalDateTime publishDate,
                                     LocalDateTime expiryDate, String issuedBy, List<PublicAttachment> attachments,
                                     String contactPhone, String contactEmail) {
    }
}
