package com.college.noticeboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The teacher responsible for one class (department + semester + class name). New student
 * registrations for that class go to this teacher for approval.
 */
@Entity
@Table(name = "class_teachers",
        uniqueConstraints = @UniqueConstraint(name = "uk_class_teacher_class",
                columnNames = {"department_id", "semester", "class_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassTeacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private Integer semester;

    /** Stored upper-case, like users.class_name. */
    @Column(name = "class_name", nullable = false, length = 20)
    private String className;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(nullable = false)
    private LocalDateTime assignedAt;
}
