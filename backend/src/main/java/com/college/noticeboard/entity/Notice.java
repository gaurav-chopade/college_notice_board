package com.college.noticeboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notices", indexes = @Index(name = "idx_notice_status", columnList = "status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice extends BaseTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private NoticeCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Creator's department at creation time; used to route faculty notices to the right HOD. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    private LocalDateTime publishDate;

    private LocalDateTime expiryDate;

    private LocalDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NoticeStatus status = NoticeStatus.DRAFT;

    @Column(length = 1000)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    /** IMPORTANT/URGENT notices always require acknowledgement; this lets NORMAL notices opt in. */
    @Column(nullable = false)
    @Builder.Default
    private boolean acknowledgementRequired = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean aiGenerated = false;

    /** PUBLIC notices are readable without signing in while published. Null on old rows means PRIVATE. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private NoticeVisibility visibility = NoticeVisibility.PRIVATE;

    /** For PUBLIC notices: Engineering, Polytechnic or both. Null for private notices. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InstitutionScope institutionScope;

    @OneToMany(mappedBy = "notice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NoticeTarget> targets = new ArrayList<>();

    public void replaceTargets(List<NoticeTarget> newTargets) {
        targets.clear();
        for (NoticeTarget t : newTargets) {
            t.setNotice(this);
            targets.add(t);
        }
    }

    public NoticeVisibility getVisibility() {
        return visibility == null ? NoticeVisibility.PRIVATE : visibility;
    }

    public boolean isPublic() {
        return getVisibility() == NoticeVisibility.PUBLIC;
    }

    /** Readable on the public site right now: public, published, already live and not past its expiry. */
    public boolean isPubliclyVisible(LocalDateTime now) {
        return isPublic() && status == NoticeStatus.PUBLISHED
                && (publishDate == null || !publishDate.isAfter(now))
                && (expiryDate == null || expiryDate.isAfter(now));
    }

    public boolean needsAcknowledgement() {
        return acknowledgementRequired || priority != Priority.NORMAL;
    }
}
