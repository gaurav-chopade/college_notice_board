package com.college.noticeboard.service;

import com.college.noticeboard.dto.AuthDtos.ChangePasswordRequest;
import com.college.noticeboard.dto.PageResponse;
import com.college.noticeboard.dto.UserDtos.*;
import com.college.noticeboard.entity.AccountStatus;
import com.college.noticeboard.entity.AuditAction;
import com.college.noticeboard.entity.Department;
import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.User;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.dto.UserMapper;
import com.college.noticeboard.repository.DepartmentRepository;
import com.college.noticeboard.repository.UserRepository;
import com.college.noticeboard.config.CurrentUserService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String q, Role role, Long departmentId, Boolean active, AccountStatus status,
                                           int page, int size) {
        Specification<User> spec = search(q).and(role == null ? null : (r, cq, cb) -> cb.equal(r.get("role"), role))
                .and(departmentId == null ? null
                        : (r, cq, cb) -> cb.equal(r.join("department", JoinType.LEFT).get("id"), departmentId))
                .and(active == null ? null : (r, cq, cb) -> cb.equal(r.get("active"), active))
                .and(status == null ? null : (r, cq, cb) -> cb.equal(r.get("status"), status));
        var result = userRepository.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("fullName")));
        return PageResponse.of(result, UserMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserMapper.toResponse(load(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest req) {
        User actor = currentUserService.currentUser();
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("An account with this email already exists");
        }
        User user = User.builder()
                .fullName(req.fullName().trim())
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .phone(req.phone())
                .active(req.active() == null || req.active())
                .build();
        applyRoleData(user, req.role(), req.departmentId(), req.semester(), req.className());
        userRepository.save(user);
        auditService.log(actor, AuditAction.USER_CREATED, "User", user.getId(), email + " as " + user.getRole());
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest req) {
        User actor = currentUserService.currentUser();
        User user = load(id);
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("An account with this email already exists");
        }
        boolean self = Objects.equals(actor.getId(), user.getId());
        if (self && (req.role() != user.getRole() || Boolean.FALSE.equals(req.active()))) {
            throw new BadRequestException("You cannot change your own role or deactivate yourself");
        }
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPhone(req.phone());
        applyRoleData(user, req.role(), req.departmentId(), req.semester(), req.className());
        // Only an actual change touches the status, so editing a pending or suspended user keeps it as is.
        if (req.active() != null && req.active() != user.isActive()) {
            user.setActive(req.active());
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.newPassword()));
        }
        auditService.log(actor, AuditAction.USER_UPDATED, "User", id, email);
        return UserMapper.toResponse(user);
    }

    /** Users are deactivated rather than hard-deleted so notices, audit logs and statistics stay consistent. */
    @Transactional
    public UserResponse setActive(Long id, boolean active) {
        User actor = currentUserService.currentUser();
        User user = load(id);
        if (Objects.equals(actor.getId(), id) && !active) {
            throw new BadRequestException("You cannot deactivate yourself");
        }
        user.setActive(active);
        user.setStatusReason(null);
        auditService.log(actor, active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED, "User", id, user.getEmail());
        return UserMapper.toResponse(user);
    }

    /** Temporarily blocks an account; the reason is shown to the person when they try to sign in. */
    @Transactional
    public UserResponse suspend(Long id, String reason) {
        User actor = currentUserService.currentUser();
        User user = load(id);
        if (Objects.equals(actor.getId(), id)) {
            throw new BadRequestException("You cannot suspend yourself");
        }
        String why = reason.trim();
        user.setStatus(AccountStatus.SUSPENDED);
        user.setStatusReason(why);
        user.setStatusReviewedBy(actor);
        user.setStatusReviewedAt(java.time.LocalDateTime.now());
        auditService.log(actor, AuditAction.USER_SUSPENDED, "User", id, user.getEmail() + RegistrationService.REASON_SEPARATOR + why);
        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserMapper.toResponse(currentUserService.currentUser());
    }

    @Transactional
    public UserResponse updateProfile(ProfileUpdateRequest req) {
        User user = currentUserService.currentUser();
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone());
        auditService.log(user, AuditAction.USER_UPDATED, "User", user.getId(), "Profile updated");
        return UserMapper.toResponse(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        User user = currentUserService.currentUser();
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        auditService.log(user, AuditAction.USER_UPDATED, "User", user.getId(), "Password changed");
    }

    /**
     * User picker for "specific users" targeting. Scoped to what the caller may target:
     * HOD → own department; FACULTY → students of own department.
     */
    @Transactional(readOnly = true)
    public List<UserLookupResponse> lookup(String q, Role role, Long departmentId, Integer semester) {
        User me = currentUserService.currentUser();
        Role effectiveRole = role;
        Long effectiveDept = departmentId;
        switch (me.getRole()) {
            case SUPER_ADMIN, PRINCIPAL -> { }
            case HOD -> effectiveDept = me.getDepartmentId();
            case FACULTY -> {
                effectiveDept = me.getDepartmentId();
                effectiveRole = Role.STUDENT;
            }
            case STUDENT -> throw new ForbiddenException("Students cannot search users");
        }
        if (effectiveDept == null && (me.getRole() == Role.HOD || me.getRole() == Role.FACULTY)) {
            return List.of();
        }
        final Role fr = effectiveRole;
        final Long fd = effectiveDept;
        Specification<User> spec = search(q)
                .and((r, cq, cb) -> cb.isTrue(r.get("active")))
                .and(fr == null ? null : (r, cq, cb) -> cb.equal(r.get("role"), fr))
                .and(fd == null ? null : (r, cq, cb) -> cb.equal(r.join("department", JoinType.LEFT).get("id"), fd))
                .and(semester == null ? null : (r, cq, cb) -> cb.equal(r.get("semester"), semester));
        return userRepository.findAll(spec, PageRequest.of(0, 20, Sort.by("fullName"))).stream()
                .map(UserMapper::toLookup).toList();
    }

    private void applyRoleData(User user, Role role, Long departmentId, Integer semester, String className) {
        user.setRole(role);
        Department dept = departmentId == null ? null : departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BadRequestException("Department not found"));
        switch (role) {
            case SUPER_ADMIN, PRINCIPAL -> {
                user.setDepartment(dept);
                user.setSemester(null);
                user.setClassName(null);
            }
            case HOD, FACULTY -> {
                if (dept == null) {
                    throw new BadRequestException(role + " must belong to a department");
                }
                user.setDepartment(dept);
                user.setSemester(null);
                user.setClassName(null);
            }
            case STUDENT -> {
                if (dept == null || semester == null || className == null || className.isBlank()) {
                    throw new BadRequestException("Students need a department, semester and class");
                }
                user.setDepartment(dept);
                user.setSemester(semester);
                user.setClassName(className.trim().toUpperCase(Locale.ROOT));
            }
        }
    }

    private static Specification<User> search(String q) {
        if (q == null || q.isBlank()) {
            return Specification.where(null);
        }
        String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return (r, cq, cb) -> cb.or(cb.like(cb.lower(r.get("fullName")), like), cb.like(cb.lower(r.get("email")), like));
    }

    private User load(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
