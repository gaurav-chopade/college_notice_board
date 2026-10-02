package com.college.noticeboard.controller;

import com.college.noticeboard.dto.AuthDtos.*;
import com.college.noticeboard.dto.MessageResponse;
import com.college.noticeboard.dto.RegistrationDtos.RegisterResponse;
import com.college.noticeboard.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Self-register as a student, teacher (FACULTY) or HOD; the account waits for approval",
            description = "Approver is chosen by the system: HOD → Principal, teacher → department HOD, "
                    + "student → class teacher (then HOD, then Principal). Rate-limited per IP.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return authService.register(request, http.getRemoteAddr());
    }

    @Operation(summary = "Sign in and receive a JWT",
            description = "401 for a wrong email or password (identical either way). After a correct password, "
                    + "403 with an 'account' object for pending, rejected, suspended or closed accounts. "
                    + "429 after repeated failures.")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    @Operation(summary = "Stateless logout: the client discards its token")
    @PostMapping("/logout")
    public MessageResponse logout() {
        return MessageResponse.ok("Signed out");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }
}
