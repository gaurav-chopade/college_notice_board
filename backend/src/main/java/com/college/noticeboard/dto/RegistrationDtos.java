package com.college.noticeboard.dto;

import com.college.noticeboard.entity.AccountStatus;
import com.college.noticeboard.entity.Role;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    /** Where an account stands; returned at sign-in for accounts that are not approved, and after registering. */
    public record AccountStatusView(AccountStatus status, Role role, String departmentName, Integer semester,
                                    String className, LocalDateTime appliedAt, String waitingFor,
                                    String reviewedByName, LocalDateTime reviewedAt, String reason,
                                    List<NoticeDtos.TimelineEvent> timeline) {
    }

    public record RegisterResponse(String message, AccountStatus status, String waitingFor) {
    }

    /** A pending account request, as shown in an approver's queue. */
    public record PendingRegistration(Long id, String fullName, String email, String phone, Role role,
                                      String departmentName, Integer semester, String className,
                                      LocalDateTime appliedAt, String waitingFor, boolean primary) {
    }

    public record RejectRegistrationRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record SuspendRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ClassTeacherRequest(@NotNull Long departmentId,
                                      @NotNull @Min(1) @Max(12) Integer semester,
                                      @NotBlank @Size(max = 20) String className,
                                      @NotNull Long teacherId) {
    }

    public record ClassTeacherResponse(Long id, Long departmentId, String departmentName, Integer semester,
                                       String className, Long teacherId, String teacherName, boolean teacherActive,
                                       long students) {
    }

    public record ClassOption(Integer semester, String className, long students) {
    }

    public record TeacherOption(Long id, String fullName) {
    }

    public record ClassTeacherOptions(List<TeacherOption> teachers, List<ClassOption> classes) {
    }
}
