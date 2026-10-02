package com.college.noticeboard.dto;

import com.college.noticeboard.entity.InstitutionType;
import com.college.noticeboard.entity.Role;
import jakarta.validation.constraints.*;

public final class AuthDtos {

    private AuthDtos() {
    }

    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$";
    public static final String PASSWORD_MESSAGE =
            "Password must be 8-64 characters with an uppercase letter, a lowercase letter, a digit and a symbol";

    /**
     * Self-registration. role is STUDENT (default), FACULTY or HOD. Every role picks a department;
     * students also give semester and class (checked in the service, because it depends on role).
     */
    public record RegisterRequest(
            Role role,
            InstitutionType institutionType,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @NotNull(message = "Select your department") Long departmentId,
            @Min(1) @Max(12) Integer semester,
            @Size(max = 20) String className,
            @Pattern(regexp = "^$|^[0-9+\\- ]{7,20}$", message = "Enter a valid phone number") String phone) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, String tokenType, long expiresInMs, UserDtos.UserResponse user) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }
}
