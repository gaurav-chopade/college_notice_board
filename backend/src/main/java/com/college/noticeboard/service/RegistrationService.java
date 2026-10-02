package com.college.noticeboard.service;

import com.college.noticeboard.config.CurrentUserService;
import com.college.noticeboard.dto.NoticeDtos.TimelineEvent;
import com.college.noticeboard.dto.RegistrationDtos.AccountStatusView;
import com.college.noticeboard.dto.RegistrationDtos.PendingRegistration;
import com.college.noticeboard.dto.UserDtos.UserResponse;
import com.college.noticeboard.dto.UserMapper;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.repository.AuditLogRepository;
import com.college.noticeboard.repository.ClassTeacherRepository;
import com.college.noticeboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who approves whom, and the approval actions themselves.
 *
 * <pre>
 * HOD      → Principal
 * Teacher  → HOD of their department            (no HOD yet → Principal)
 * Student  → class teacher of their class        (none → department HOD → Principal)
 * </pre>
 * Anyone higher up the chain may also act, so a request never waits on a missing approver:
 * an HOD can approve any student or teacher of their department, the Principal and the super
 * admin can approve anyone. The applicant never chooses their approver.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    public static final String REASON_SEPARATOR = NoticeTimelineService.REASON_SEPARATOR;

    private static final Set<AuditAction> ACCOUNT_EVENTS = EnumSet.of(AuditAction.USER_REGISTERED,
            AuditAction.USER_APPROVED, AuditAction.USER_REJECTED, AuditAction.USER_SUSPENDED,
            AuditAction.USER_ACTIVATED, AuditAction.USER_DEACTIVATED);

    private final UserRepository userRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CurrentUserService currentUserService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // ------------------------------------------------------------------ routing

    enum Level { CLASS_TEACHER, HOD, PRINCIPAL }

    /** The level a request waits at, the people at that level, and a sentence naming them. */
    record Route(Level level, List<User> approvers, String description) {
    }

    Route route(User applicant) {
        Department dept = applicant.getDepartment();
        if (applicant.getRole() == Role.STUDENT && dept != null) {
            Optional<User> teacher = classTeacher(dept.getId(), applicant.getSemester(), applicant.getClassName());
            if (teacher.isPresent()) {
                return new Route(Level.CLASS_TEACHER, List.of(teacher.get()), "the class teacher of " + dept.getName()
                        + " semester " + applicant.getSemester() + " class " + applicant.getClassName()
                        + " (" + teacher.get().getFullName() + ")");
            }
        }
        if ((applicant.getRole() == Role.STUDENT || applicant.getRole() == Role.FACULTY) && dept != null) {
            List<User> hods = userRepository.findByRoleAndDepartment_IdAndActiveTrue(Role.HOD, dept.getId());
            if (!hods.isEmpty()) {
                return new Route(Level.HOD, hods, "the " + dept.getName() + " HOD"
                        + (hods.size() == 1 ? " (" + hods.get(0).getFullName() + ")" : ""));
            }
        }
        List<User> principals = userRepository.findByRoleAndActiveTrue(Role.PRINCIPAL);
        return new Route(Level.PRINCIPAL, principals, principals.isEmpty() ? "the administrator" : "the Principal");
    }

    public String waitingFor(User applicant) {
        return route(applicant).description();
    }

    /** The approved teacher assigned to a class, if any. */
    public Optional<User> classTeacher(Long departmentId, Integer semester, String className) {
        if (departmentId == null || semester == null || className == null) {
            return Optional.empty();
        }
        return classTeacherRepository.findByDepartmentIdAndSemesterAndClassName(departmentId, semester,
                        className.toUpperCase(Locale.ROOT))
                .map(ClassTeacher::getTeacher)
                .filter(t -> t.getStatus() == AccountStatus.APPROVED && t.getRole() == Role.FACULTY);
    }

    boolean canReview(User reviewer, User applicant) {
        return switch (reviewer.getRole()) {
            case SUPER_ADMIN, PRINCIPAL -> true;
            case HOD -> (applicant.getRole() == Role.FACULTY || applicant.getRole() == Role.STUDENT)
                    && reviewer.getDepartmentId() != null
                    && Objects.equals(reviewer.getDepartmentId(), applicant.getDepartmentId());
            case FACULTY -> applicant.getRole() == Role.STUDENT
                    && classTeacher(applicant.getDepartmentId(), applicant.getSemester(), applicant.getClassName())
                    .map(t -> Objects.equals(t.getId(), reviewer.getId())).orElse(false);
            case STUDENT -> false;
        };
    }

    private boolean isPrimary(User reviewer, Route route) {
        if (route.approvers().stream().anyMatch(a -> Objects.equals(a.getId(), reviewer.getId()))) {
            return true;
        }
        return route.level() == Level.PRINCIPAL
                && (reviewer.getRole() == Role.PRINCIPAL || (reviewer.getRole() == Role.SUPER_ADMIN && route.approvers().isEmpty()));
    }

    // ------------------------------------------------------------------ registration side

    /** One HOD per department: a department with an approved HOD takes no further HOD requests or approvals. */
    public void ensureHodVacancy(Department dept, Long exceptUserId) {
        userRepository.findByRoleAndDepartment_IdAndActiveTrue(Role.HOD, dept.getId()).stream()
                .filter(h -> !Objects.equals(h.getId(), exceptUserId))
                .findFirst()
                .ifPresent(h -> {
                    throw new BadRequestException(dept.getName() + " already has an HOD (" + h.getFullName()
                            + "). Ask the Principal if this needs to change.");
                });
    }

    /** In-app notice to the people a new request is waiting for. */
    public void notifyApprovers(User applicant) {
        Route r = route(applicant);
        List<User> recipients = r.approvers().isEmpty() ? userRepository.findByRoleAndActiveTrue(Role.SUPER_ADMIN) : r.approvers();
        String message = "Account request: " + applicant.getFullName() + " (" + roleLabel(applicant.getRole())
                + (applicant.getDepartment() == null ? "" : ", " + applicant.getDepartment().getName())
                + (applicant.getSemester() == null ? "" : ", semester " + applicant.getSemester() + " class " + applicant.getClassName())
                + ")";
        recipients.forEach(u -> notificationService.notifyLink(u, message, "/account-requests"));
    }

    // ------------------------------------------------------------------ approver side

    @Transactional(readOnly = true)
    public List<PendingRegistration> queue() {
        User me = currentUserService.currentUser();
        List<PendingRegistration> out = new ArrayList<>();
        for (User applicant : userRepository.findByStatusOrderByCreatedAtAsc(AccountStatus.PENDING)) {
            if (!canReview(me, applicant)) {
                continue;
            }
            Route r = route(applicant);
            boolean primary = isPrimary(me, r);
            // The Principal sees HODs and anything escalated to them; HODs see their whole department.
            if (me.getRole() == Role.PRINCIPAL && !primary) {
                continue;
            }
            out.add(new PendingRegistration(applicant.getId(), applicant.getFullName(), applicant.getEmail(),
                    applicant.getPhone(), applicant.getRole(),
                    applicant.getDepartment() == null ? null : applicant.getDepartment().getName(),
                    applicant.getSemester(), applicant.getClassName(), appliedAt(applicant), r.description(), primary));
        }
        return out;
    }

    @Transactional
    public UserResponse approve(Long id) {
        User reviewer = currentUserService.currentUser();
        User applicant = pendingFor(reviewer, id);
        if (applicant.getRole() == Role.HOD && applicant.getDepartment() != null) {
            ensureHodVacancy(applicant.getDepartment(), applicant.getId());
        }
        applicant.setStatus(AccountStatus.APPROVED);
        applicant.setStatusReason(null);
        applicant.setStatusReviewedBy(reviewer);
        applicant.setStatusReviewedAt(LocalDateTime.now());
        auditService.log(reviewer, AuditAction.USER_APPROVED, "User", id,
                applicant.getEmail() + " as " + roleLabel(applicant.getRole()));
        notificationService.notifyLink(applicant, "Welcome! Your account was approved by " + reviewer.getFullName() + ".", "/");
        emailService.sendSingle(applicant.getEmail(), "Your college notice board account is approved",
                "Hello " + applicant.getFullName() + ",\n\nYour account was approved by " + reviewer.getFullName()
                        + ". You can sign in now:\n\n" + frontendUrl + "/login");
        return UserMapper.toResponse(applicant);
    }

    @Transactional
    public UserResponse reject(Long id, String reason) {
        User reviewer = currentUserService.currentUser();
        User applicant = pendingFor(reviewer, id);
        String why = reason.trim();
        applicant.setStatus(AccountStatus.REJECTED);
        applicant.setStatusReason(why);
        applicant.setStatusReviewedBy(reviewer);
        applicant.setStatusReviewedAt(LocalDateTime.now());
        auditService.log(reviewer, AuditAction.USER_REJECTED, "User", id, applicant.getEmail() + REASON_SEPARATOR + why);
        emailService.sendSingle(applicant.getEmail(), "Your college notice board registration",
                "Hello " + applicant.getFullName() + ",\n\nYour registration was not approved.\nReason: " + why
                        + "\n\nYou can correct your details and apply again:\n\n" + frontendUrl + "/register");
        return UserMapper.toResponse(applicant);
    }

    /** Requests waiting for the caller specifically (for the navigation badge). */
    @Transactional(readOnly = true)
    public long queueCount() {
        return queue().stream().filter(PendingRegistration::primary).count();
    }

    private User pendingFor(User reviewer, Long id) {
        User applicant = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account request not found"));
        if (!canReview(reviewer, applicant)) {
            throw new ForbiddenException("You cannot review this account request");
        }
        if (applicant.getStatus() != AccountStatus.PENDING) {
            throw new BadRequestException("This request has already been handled");
        }
        return applicant;
    }

    // ------------------------------------------------------------------ applicant's view

    /** Status, who it waits for, the reason if any, and the history of the account. */
    @Transactional(readOnly = true)
    public AccountStatusView statusView(User user) {
        AccountStatus status = user.getStatus();
        List<AuditLog> logs = auditLogRepository
                .findByEntityTypeAndEntityIdAndActionInOrderByTimestampAscIdAsc("User", user.getId(), ACCOUNT_EVENTS);
        Set<Long> actorIds = logs.stream().map(AuditLog::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> actors = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<TimelineEvent> timeline = logs.stream().map(l -> {
            User a = l.getUserId() == null ? null : actors.get(l.getUserId());
            String name = l.getUserId() == null ? "System" : Objects.equals(l.getUserId(), user.getId()) ? "You"
                    : a != null ? a.getFullName() : "Staff";
            return new TimelineEvent(l.getAction(), name, a == null || Objects.equals(l.getUserId(), user.getId()) ? null : a.getRole(),
                    l.getTimestamp(), note(l), 1);
        }).toList();
        User reviewer = user.getStatusReviewedBy();
        return new AccountStatusView(status, user.getRole(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.getSemester(), user.getClassName(), appliedAt(user),
                status == AccountStatus.PENDING ? waitingFor(user) : null,
                reviewer == null ? null : reviewer.getFullName(), user.getStatusReviewedAt(),
                status == AccountStatus.REJECTED || status == AccountStatus.SUSPENDED ? user.getStatusReason() : null,
                timeline);
    }

    public static String statusMessage(AccountStatusView v) {
        return switch (v.status()) {
            case PENDING -> "Your account is waiting for approval by " + v.waitingFor() + ".";
            case REJECTED -> "Your registration was not approved.";
            case SUSPENDED -> "Your account is suspended.";
            case DEACTIVATED -> "Your account has been closed. Contact the college office if this is a mistake.";
            case APPROVED -> "Your account is active.";
        };
    }

    private LocalDateTime appliedAt(User u) {
        return auditLogRepository.findByEntityTypeAndEntityIdAndActionInOrderByTimestampAscIdAsc(
                        "User", u.getId(), EnumSet.of(AuditAction.USER_REGISTERED)).stream()
                .reduce((first, second) -> second).map(AuditLog::getTimestamp).orElse(u.getCreatedAt());
    }

    private static String note(AuditLog l) {
        String d = l.getDescription();
        if (d == null) {
            return null;
        }
        return switch (l.getAction()) {
            case USER_REJECTED, USER_SUSPENDED -> d.contains(REASON_SEPARATOR)
                    ? d.substring(d.indexOf(REASON_SEPARATOR) + REASON_SEPARATOR.length()) : null;
            case USER_REGISTERED -> d.startsWith("Applied") || d.startsWith("Re-applied") ? d : null;
            default -> null;
        };
    }

    public static String roleLabel(Role role) {
        return switch (role) {
            case STUDENT -> "student";
            case FACULTY -> "teacher";
            case HOD -> "HOD";
            case PRINCIPAL -> "principal";
            case SUPER_ADMIN -> "administrator";
        };
    }
}
