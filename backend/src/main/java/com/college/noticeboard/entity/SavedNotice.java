package com.college.noticeboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A notice a user has saved to come back to (their personal reading list). */
@Entity
@Table(name = "saved_notices",
        uniqueConstraints = @UniqueConstraint(name = "uk_saved_notice_user", columnNames = {"notice_id", "user_id"}),
        indexes = @Index(name = "idx_saved_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime savedAt;
}
