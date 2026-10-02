package com.college.noticeboard.service;

import com.college.noticeboard.dto.NoticeDtos.TargetRequest;
import com.college.noticeboard.dto.TargetOptionsResponse;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.repository.DepartmentRepository;
import com.college.noticeboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates, normalises and authorises the target audience a user picks for a notice.
 * <ul>
 *     <li>SUPER_ADMIN / PRINCIPAL: any target.</li>
 *     <li>HOD: only their own department (department, faculty, students, semester, class, specific users).</li>
 *     <li>FACULTY: only students of their own department (semester, class, all students, specific students).</li>
 *     <li>STUDENT: cannot target anyone.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TargetPolicy {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public List<NoticeTarget> buildTargets(User creator, List<TargetRequest> requests) {
        if (creator.getRole() == Role.STUDENT) {
            throw new ForbiddenException("Students cannot create notices");
        }
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("Select at least one target audience");
        }
        Map<String, NoticeTarget> unique = new LinkedHashMap<>();
        for (TargetRequest req : requests) {
            NoticeTarget t = normalise(req);
            authorise(creator, t);
            unique.putIfAbsent(key(t), t);
        }
        return new ArrayList<>(unique.values());
    }

    public TargetOptionsResponse optionsFor(User user) {
        Department d = user.getDepartment();
        return switch (user.getRole()) {
            case SUPER_ADMIN, PRINCIPAL -> new TargetOptionsResponse(List.of(TargetType.values()),
                    List.of(Role.PRINCIPAL, Role.HOD, Role.FACULTY, Role.STUDENT), null, null, false);
            case HOD -> new TargetOptionsResponse(
                    List.of(TargetType.DEPARTMENT, TargetType.ROLE_DEPARTMENT, TargetType.SEMESTER,
                            TargetType.CLASS, TargetType.SPECIFIC_USER),
                    List.of(Role.FACULTY, Role.STUDENT),
                    d == null ? null : d.getId(), d == null ? null : d.getName(), false);
            case FACULTY -> new TargetOptionsResponse(
                    List.of(TargetType.ROLE_DEPARTMENT, TargetType.SEMESTER, TargetType.CLASS, TargetType.SPECIFIC_USER),
                    List.of(Role.STUDENT),
                    d == null ? null : d.getId(), d == null ? null : d.getName(), true);
            case STUDENT -> new TargetOptionsResponse(List.of(), List.of(), null, null, false);
        };
    }

    /** Default audience used when an AI suggestion is not allowed for the user. */
    public TargetRequest defaultTargetFor(User user) {
        return switch (user.getRole()) {
            case SUPER_ADMIN, PRINCIPAL -> new TargetRequest(TargetType.COLLEGE, null, null, null, null, null);
            case HOD -> new TargetRequest(TargetType.DEPARTMENT, null, user.getDepartmentId(), null, null, null);
            case FACULTY -> new TargetRequest(TargetType.ROLE_DEPARTMENT, Role.STUDENT, user.getDepartmentId(), null, null, null);
            case STUDENT -> throw new ForbiddenException("Students cannot create notices");
        };
    }

    NoticeTarget normalise(TargetRequest r) {
        if (r.targetType() == null) {
            throw new BadRequestException("Target type is required");
        }
        NoticeTarget.NoticeTargetBuilder b = NoticeTarget.builder().targetType(r.targetType());
        switch (r.targetType()) {
            case COLLEGE -> { /* everyone */ }
            case ROLE -> b.targetRole(require(r.targetRole(), "Select a role"));
            case DEPARTMENT -> b.department(department(r.departmentId()));
            case ROLE_DEPARTMENT -> b.targetRole(require(r.targetRole(), "Select a role"))
                    .department(department(r.departmentId()));
            case SEMESTER -> b.targetRole(r.targetRole() == null ? Role.STUDENT : r.targetRole())
                    .department(department(r.departmentId()))
                    .semester(require(r.semester(), "Select a semester"));
            case CLASS -> b.targetRole(r.targetRole() == null ? Role.STUDENT : r.targetRole())
                    .department(department(r.departmentId()))
                    .semester(require(r.semester(), "Select a semester"))
                    .className(className(r.className()));
            case SPECIFIC_USER -> b.user(user(r.userId()));
        }
        return b.build();
    }

    void authorise(User creator, NoticeTarget t) {
        switch (creator.getRole()) {
            case SUPER_ADMIN, PRINCIPAL -> {
                // unrestricted
            }
            case HOD -> {
                Long own = requireOwnDepartment(creator);
                if (t.getTargetType() == TargetType.COLLEGE || t.getTargetType() == TargetType.ROLE) {
                    throw new ForbiddenException("HODs can only target their own department");
                }
                if (t.getTargetType() == TargetType.SPECIFIC_USER) {
                    if (!own.equals(t.getUser().getDepartmentId())) {
                        throw new ForbiddenException("HODs can only select users from their own department");
                    }
                    return;
                }
                if (!own.equals(t.getDepartment().getId())) {
                    throw new ForbiddenException("HODs can only target their own department");
                }
                if (t.getTargetRole() != null && t.getTargetRole() != Role.FACULTY && t.getTargetRole() != Role.STUDENT) {
                    throw new ForbiddenException("HODs can target faculty or students of their department");
                }
            }
            case FACULTY -> {
                Long own = requireOwnDepartment(creator);
                switch (t.getTargetType()) {
                    case SEMESTER, CLASS, ROLE_DEPARTMENT -> {
                        if (t.getTargetRole() != Role.STUDENT) {
                            throw new ForbiddenException("Faculty can only target students");
                        }
                        if (!own.equals(t.getDepartment().getId())) {
                            throw new ForbiddenException("Faculty can only target students of their own department");
                        }
                    }
                    case SPECIFIC_USER -> {
                        User u = t.getUser();
                        if (u.getRole() != Role.STUDENT || !own.equals(u.getDepartmentId())) {
                            throw new ForbiddenException("Faculty can only select students of their own department");
                        }
                    }
                    default -> throw new ForbiddenException("Faculty can target students by semester, class or department only");
                }
            }
            case STUDENT -> throw new ForbiddenException("Students cannot create notices");
        }
    }

    private Long requireOwnDepartment(User creator) {
        if (creator.getDepartmentId() == null) {
            throw new ForbiddenException("Your account is not linked to a department");
        }
        return creator.getDepartmentId();
    }

    private Department department(Long id) {
        if (id == null) {
            throw new BadRequestException("Select a department");
        }
        return departmentRepository.findById(id).orElseThrow(() -> new BadRequestException("Department not found"));
    }

    private User user(Long id) {
        if (id == null) {
            throw new BadRequestException("Select a user");
        }
        User u = userRepository.findById(id).orElseThrow(() -> new BadRequestException("Selected user not found"));
        if (!u.isActive()) {
            throw new BadRequestException("Selected user " + u.getFullName() + " is inactive");
        }
        return u;
    }

    private static String className(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Enter a class name");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private static String key(NoticeTarget t) {
        return t.getTargetType() + "|" + t.getTargetRole() + "|"
                + (t.getDepartment() == null ? null : t.getDepartment().getId()) + "|" + t.getSemester() + "|"
                + t.getClassName() + "|" + (t.getUser() == null ? null : t.getUser().getId());
    }
}
