package com.college.noticeboard.dto;

import com.college.noticeboard.entity.AccountStatus;
import com.college.noticeboard.entity.InstitutionType;
import com.college.noticeboard.entity.Role;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(Long id, String fullName, String email, Role role, Long departmentId,
                               String departmentName, Integer semester, String className, String phone,
                               boolean active, AccountStatus status, String statusReason, LocalDateTime createdAt,
                               InstitutionType institutionType) {
    }

    public record UserLookupResponse(Long id, String fullName, String email, Role role, String departmentName,
                                     Integer semester, String className) {
    }

    public record UserCreateRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MESSAGE) String password,
            @NotNull Role role,
            Long departmentId,
            @Min(1) @Max(12) Integer semester,
            @Size(max = 20) String className,
            @Size(max = 20) String phone,
            Boolean active) {
    }

    public record UserUpdateRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotNull Role role,
            Long departmentId,
            @Min(1) @Max(12) Integer semester,
            @Size(max = 20) String className,
            @Size(max = 20) String phone,
            Boolean active,
            @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MESSAGE) String newPassword) {
    }

    public record ProfileUpdateRequest(@NotBlank @Size(max = 120) String fullName, @Size(max = 20) String phone) {
    }
}
