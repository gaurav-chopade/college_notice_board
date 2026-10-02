package com.college.noticeboard.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "departments",
        uniqueConstraints = @UniqueConstraint(name = "uk_department_name_institution", columnNames = {"name", "institution_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique within its institution: Engineering and Polytechnic may both have "Computer Engineering". */
    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(length = 500)
    private String description;

    /** Rows created before this column existed are treated as ENGINEERING (and back-filled at startup). */
    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type", length = 20)
    private InstitutionType institutionType;

    /** Programme students of this department study, e.g. "B.Tech" or "Diploma". */
    @Column(length = 60)
    private String programName;

    /** Number of semesters in the programme (8 for a B.Tech, 6 for a diploma). */
    private Integer semesterCount;

    public InstitutionType getInstitutionType() {
        return institutionType == null ? InstitutionType.ENGINEERING : institutionType;
    }

    public int getSemesterCount() {
        return semesterCount != null ? semesterCount : getInstitutionType() == InstitutionType.POLYTECHNIC ? 6 : 8;
    }

    public String getProgramName() {
        return programName != null && !programName.isBlank() ? programName
                : getInstitutionType() == InstitutionType.POLYTECHNIC ? "Diploma" : "B.Tech";
    }
}
