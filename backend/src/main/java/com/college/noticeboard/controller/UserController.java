package com.college.noticeboard.controller;

import com.college.noticeboard.dto.AuthDtos.ChangePasswordRequest;
import com.college.noticeboard.dto.MessageResponse;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.dto.UserDtos.*;
import com.college.noticeboard.dto.RegistrationDtos.SuspendRequest;
import com.college.noticeboard.entity.AccountStatus;
import com.college.noticeboard.entity.Role;
import com.college.noticeboard.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Role role,
                                           @RequestParam(required = false) Long departmentId,
                                           @RequestParam(required = false) Boolean active,
                                           @RequestParam(required = false) AccountStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return userService.list(q, role, departmentId, active, status, page, size);
    }

    @Operation(summary = "Search users the caller may target (specific-user targeting)")
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PRINCIPAL','HOD','FACULTY')")
    public List<UserLookupResponse> lookup(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Role role,
                                           @RequestParam(required = false) Long departmentId,
                                           @RequestParam(required = false) Integer semester) {
        return userService.lookup(q, role, departmentId, semester);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return userService.me();
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(request);
    }

    @PutMapping("/me/password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return MessageResponse.ok("Password changed");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    @Operation(summary = "Deactivate a user (soft delete keeps notices and audit history intact)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserResponse delete(@PathVariable Long id) {
        return userService.setActive(id, false);
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserResponse activate(@PathVariable Long id) {
        return userService.setActive(id, true);
    }

    @Operation(summary = "Suspend an account with a reason the person sees at sign-in")
    @PutMapping("/{id}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserResponse suspend(@PathVariable Long id, @Valid @RequestBody SuspendRequest request) {
        return userService.suspend(id, request.reason());
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserResponse deactivate(@PathVariable Long id) {
        return userService.setActive(id, false);
    }
}
