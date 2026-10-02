package com.college.noticeboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_role", columnList = "role"),
        @Index(name = "idx_user_dept_sem_class", columnList = "department_id,semester,class_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    /** BCrypt hash. Never plain text. */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Only meaningful for students. */
    private Integer semester;

    /** Section/class name such as "A". Only meaningful for students. */
    @Column(name = "class_name", length = 20)
    private String className;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** SHA-256 hash of the password reset token; the raw token is only ever emailed. */
    @Column(length = 128)
    private String resetTokenHash;

    private LocalDateTime resetTokenExpiry;

    /**
     * Account lifecycle. {@link #active} is kept in step with it ({@code active == (status == APPROVED)})
     * so existing queries on the active flag keep working. Rows created before this column existed
     * are read as APPROVED or DEACTIVATED from their active flag.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AccountStatus status;

    /** Why the account was rejected or suspended; shown to the person when they try to sign in. */
    @Column(length = 500)
    private String statusReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_reviewed_by")
    private User statusReviewedBy;

    private LocalDateTime statusReviewedAt;

    public void setStatus(AccountStatus status) {
        this.status = status;
        this.active = status == AccountStatus.APPROVED;
    }

    /** Kept for existing callers: true approves, false deactivates. */
    public void setActive(boolean active) {
        this.active = active;
        this.status = active ? AccountStatus.APPROVED : AccountStatus.DEACTIVATED;
    }

    public AccountStatus getStatus() {
        return status != null ? status : (active ? AccountStatus.APPROVED : AccountStatus.DEACTIVATED);
    }

    @PrePersist
    @PreUpdate
    protected void syncStatus() {
        if (status == null) {
            status = active ? AccountStatus.APPROVED : AccountStatus.DEACTIVATED;
        }
        active = status == AccountStatus.APPROVED;
    }

    public Long getDepartmentId() {
        return department == null ? null : department.getId();
    }

    /** Engineering or Polytechnic, from the department; null for college-wide roles. */
    public InstitutionType getInstitutionType() {
        return department == null ? null : department.getInstitutionType();
    }
}
