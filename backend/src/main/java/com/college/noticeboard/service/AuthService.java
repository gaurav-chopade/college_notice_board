package com.college.noticeboard.service;

import com.college.noticeboard.dto.AuthDtos.*;
import com.college.noticeboard.dto.MessageResponse;
import com.college.noticeboard.dto.RegistrationDtos.AccountStatusView;
import com.college.noticeboard.dto.RegistrationDtos.RegisterResponse;
import com.college.noticeboard.entity.AccountStatus;
import com.college.noticeboard.entity.AuditAction;
import com.college.noticeboard.entity.Department;
import com.college.noticeboard.entity.InstitutionType;
import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.User;
import com.college.noticeboard.exception.AccountStatusException;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.dto.UserMapper;
import com.college.noticeboard.service.EmailService;
import com.college.noticeboard.repository.DepartmentRepository;
import com.college.noticeboard.repository.UserRepository;
import com.college.noticeboard.config.JwtService;
import com.college.noticeboard.config.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationService registrationService;
    private final AuthRateLimiter rateLimiter;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final EmailService emailService;

    @Value("${app.auth.auto-activate-registrations:false}")
    private boolean autoActivate;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private volatile String dummyHash;

    /**
     * Self-registration as a student, teacher (FACULTY) or HOD. The account starts PENDING and goes
     * to the approver {@link RegistrationService} picks; the applicant never chooses who approves.
     * A previously REJECTED application can be replaced by applying again with the same email.
     * {@code app.auth.auto-activate-registrations} (development only) skips approval for students,
     * never for staff.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest req, String clientIp) {
        Role role = req.role() == null ? Role.STUDENT : req.role();
        if (role != Role.STUDENT && role != Role.FACULTY && role != Role.HOD) {
            throw new BadRequestException("You can register as a student, teacher or HOD");
        }
        Department dept = departmentRepository.findById(req.departmentId())
                .orElseThrow(() -> new BadRequestException("Department not found"));
        // The wing decides who can approve (an Engineering HOD never sees Polytechnic requests),
        // so the department must belong to the wing the applicant chose.
        if (req.institutionType() != null && req.institutionType() != dept.getInstitutionType()) {
            throw new BadRequestException(dept.getName() + " is not a "
                    + (req.institutionType() == InstitutionType.POLYTECHNIC ? "Polytechnic" : "Engineering") + " department");
        }
        String className = null;
        if (role == Role.STUDENT) {
            if (req.semester() == null) {
                throw new BadRequestException("Select your semester");
            }
            if (req.semester() > dept.getSemesterCount()) {
                throw new BadRequestException(dept.getProgramName() + " in " + dept.getName() + " has "
                        + dept.getSemesterCount() + " semesters");
            }
            if (req.className() == null || req.className().isBlank()) {
                throw new BadRequestException("Enter your class or division");
            }
            className = req.className().trim().toUpperCase(Locale.ROOT);
        }
        if (role == Role.HOD) {
            registrationService.ensureHodVacancy(dept, null);
        }

        String email = normaliseEmail(req.email());
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null && existing.getStatus() != AccountStatus.REJECTED) {
            throw new BadRequestException("An account with this email already exists. Sign in to see its status.");
        }
        rateLimiter.checkAndRecordRegistration(clientIp);

        boolean autoApprove = autoActivate && role == Role.STUDENT;
        User user = existing != null ? existing : new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(role);
        user.setDepartment(dept);
        user.setSemester(role == Role.STUDENT ? req.semester() : null);
        user.setClassName(className);
        user.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        user.setStatus(autoApprove ? AccountStatus.APPROVED : AccountStatus.PENDING);
        user.setStatusReason(null);
        user.setStatusReviewedBy(null);
        user.setStatusReviewedAt(null);
        userRepository.save(user);

        String what = (existing != null ? "Re-applied as " : "Applied as ") + RegistrationService.roleLabel(role)
                + ", " + dept.getName() + (role == Role.STUDENT ? " semester " + req.semester() + " class " + className : "");
        auditService.log(user, AuditAction.USER_REGISTERED, "User", user.getId(), what);

        if (autoApprove) {
            return new RegisterResponse("Registration successful. You can sign in now.", AccountStatus.APPROVED, null);
        }
        registrationService.notifyApprovers(user);
        String waitingFor = registrationService.waitingFor(user);
        return new RegisterResponse("Request sent. It will be reviewed by " + waitingFor
                + ". Sign in any time to check its status.", AccountStatus.PENDING, waitingFor);
    }

    /**
     * Checks the password before revealing anything about the account, so the response for an
     * unknown email and a wrong password is identical. Only then does a non-approved account get
     * its status (pending, rejected with reason, suspended) instead of a token.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req, String clientIp) {
        String email = normaliseEmail(req.email());
        rateLimiter.checkLogin(email, clientIp);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean ok;
        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash());   // same work as a real check
            ok = false;
        } else {
            ok = passwordEncoder.matches(req.password(), user.getPassword());
        }
        if (!ok) {
            rateLimiter.recordLoginFailure(email, clientIp);
            throw new BadCredentialsException("Invalid email or password");
        }
        rateLimiter.recordLoginSuccess(email, clientIp);
        if (user.getStatus() != AccountStatus.APPROVED) {
            AccountStatusView view = registrationService.statusView(user);
            throw new AccountStatusException(RegistrationService.statusMessage(view), view);
        }
        UserPrincipal principal = new UserPrincipal(user);
        return new AuthResponse(jwtService.generateToken(principal), "Bearer", jwtService.getExpirationMs(),
                UserMapper.toResponse(user));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("not-a-real-password-" + RANDOM.nextInt());
        }
        return dummyHash;
    }

    /** Always returns the same message so the endpoint cannot be used to discover registered emails. */
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest req) {
        userRepository.findByEmailIgnoreCase(normaliseEmail(req.email())).filter(User::isActive).ifPresent(user -> {
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            user.setResetTokenHash(sha256(token));
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));
            String link = frontendUrl + "/reset-password?token=" + token;
            if (emailService.isEnabled()) {
                emailService.sendSingle(user.getEmail(), "Reset your notice board password",
                        "Use this link within 30 minutes to set a new password:\n\n" + link
                                + "\n\nIf you did not request this, ignore this email.");
            } else {
                log.info("Mail disabled. Password reset link for {}: {}", user.getEmail(), link);
            }
        });
        return MessageResponse.ok("If the email is registered, a reset link has been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest req) {
        User user = userRepository.findByResetTokenHash(sha256(req.token()))
                .filter(u -> u.getResetTokenExpiry() != null && u.getResetTokenExpiry().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BadRequestException("The reset link is invalid or has expired"));
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setResetTokenHash(null);
        user.setResetTokenExpiry(null);
        auditService.log(user, AuditAction.USER_UPDATED, "User", user.getId(), "Password reset");
        return MessageResponse.ok("Password updated. You can sign in now.");
    }

    static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
