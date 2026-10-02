package com.college.noticeboard.service;

import com.college.noticeboard.config.CurrentUserService;
import com.college.noticeboard.dto.RegistrationDtos.*;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.repository.ClassTeacherRepository;
import com.college.noticeboard.repository.DepartmentRepository;
import com.college.noticeboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Class teacher assignments. An HOD manages their own department; the Principal and the
 * super admin can manage any department. The teacher must be an approved teacher of that department.
 */
@Service
@RequiredArgsConstructor
public class ClassTeacherService {

    private static final List<AccountStatus> COUNTED = List.of(AccountStatus.APPROVED, AccountStatus.PENDING);

    private final ClassTeacherRepository classTeacherRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<ClassTeacherResponse> list(Long departmentId) {
        User me = currentUserService.currentUser();
        Long dept = scopedDepartment(me, departmentId, false);
        List<ClassTeacher> rows = dept == null
                ? classTeacherRepository.findAllByOrderByDepartmentIdAscSemesterAscClassNameAsc()
                : classTeacherRepository.findByDepartmentIdOrderBySemesterAscClassNameAsc(dept);
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        return rows.stream().map(ct -> {
            Long d = ct.getDepartment().getId();
            long students = counts.computeIfAbsent(d, this::studentCounts)
                    .getOrDefault(ct.getSemester() + "|" + ct.getClassName(), 0L);
            User t = ct.getTeacher();
            return new ClassTeacherResponse(ct.getId(), d, ct.getDepartment().getName(), ct.getSemester(),
                    ct.getClassName(), t.getId(), t.getFullName(), t.getStatus() == AccountStatus.APPROVED, students);
        }).toList();
    }

    /** Approved teachers of the department, and the classes its students are in. */
    @Transactional(readOnly = true)
    public ClassTeacherOptions options(Long departmentId) {
        User me = currentUserService.currentUser();
        Long dept = scopedDepartment(me, departmentId, true);
        List<TeacherOption> teachers = userRepository.findByRoleAndDepartment_IdAndActiveTrue(Role.FACULTY, dept).stream()
                .sorted(Comparator.comparing(User::getFullName))
                .map(u -> new TeacherOption(u.getId(), u.getFullName())).toList();
        List<ClassOption> classes = userRepository.countStudentsByClass(dept, COUNTED).stream()
                .map(r -> new ClassOption((Integer) r[0], (String) r[1], (Long) r[2])).toList();
        return new ClassTeacherOptions(teachers, classes);
    }

    /** Creates or replaces the class teacher of one class. */
    @Transactional
    public ClassTeacherResponse assign(ClassTeacherRequest req) {
        User me = currentUserService.currentUser();
        Long deptId = scopedDepartment(me, req.departmentId(), true);
        Department dept = departmentRepository.findById(deptId).orElseThrow(() -> new BadRequestException("Department not found"));
        User teacher = userRepository.findById(req.teacherId()).orElseThrow(() -> new BadRequestException("Teacher not found"));
        if (teacher.getRole() != Role.FACULTY || teacher.getStatus() != AccountStatus.APPROVED
                || !Objects.equals(teacher.getDepartmentId(), deptId)) {
            throw new BadRequestException("Choose an active teacher of " + dept.getName());
        }
        String cls = req.className().trim().toUpperCase(Locale.ROOT);
        ClassTeacher ct = classTeacherRepository.findByDepartmentIdAndSemesterAndClassName(deptId, req.semester(), cls)
                .orElseGet(() -> ClassTeacher.builder().department(dept).semester(req.semester()).className(cls).build());
        ct.setTeacher(teacher);
        ct.setAssignedAt(LocalDateTime.now());
        classTeacherRepository.save(ct);
        auditService.log(me, AuditAction.CLASS_TEACHER_ASSIGNED, "ClassTeacher", ct.getId(),
                dept.getName() + " semester " + req.semester() + " class " + cls + " → " + teacher.getFullName());
        long students = studentCounts(deptId).getOrDefault(req.semester() + "|" + cls, 0L);
        return new ClassTeacherResponse(ct.getId(), deptId, dept.getName(), ct.getSemester(), cls,
                teacher.getId(), teacher.getFullName(), true, students);
    }

    @Transactional
    public void remove(Long id) {
        User me = currentUserService.currentUser();
        ClassTeacher ct = classTeacherRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        scopedDepartment(me, ct.getDepartment().getId(), true);
        classTeacherRepository.delete(ct);
        auditService.log(me, AuditAction.CLASS_TEACHER_REMOVED, "ClassTeacher", id,
                ct.getDepartment().getName() + " semester " + ct.getSemester() + " class " + ct.getClassName());
    }

    /** HODs are pinned to their own department; others may choose (or, for listing, see all). */
    private Long scopedDepartment(User me, Long requested, boolean required) {
        return switch (me.getRole()) {
            case SUPER_ADMIN, PRINCIPAL -> {
                if (required && requested == null) {
                    throw new BadRequestException("Select a department");
                }
                yield requested;
            }
            case HOD -> {
                if (me.getDepartmentId() == null) {
                    throw new ForbiddenException("Your account is not linked to a department");
                }
                if (requested != null && !requested.equals(me.getDepartmentId())) {
                    throw new ForbiddenException("HODs manage class teachers of their own department only");
                }
                yield me.getDepartmentId();
            }
            default -> throw new ForbiddenException("Only HODs, the Principal and the administrator manage class teachers");
        };
    }

    private Map<String, Long> studentCounts(Long deptId) {
        Map<String, Long> m = new HashMap<>();
        for (Object[] r : userRepository.countStudentsByClass(deptId, COUNTED)) {
            m.put(r[0] + "|" + r[1], (Long) r[2]);
        }
        return m;
    }
}
