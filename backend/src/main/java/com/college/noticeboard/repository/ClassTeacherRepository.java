package com.college.noticeboard.repository;

import com.college.noticeboard.entity.ClassTeacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {

    Optional<ClassTeacher> findByDepartmentIdAndSemesterAndClassName(Long departmentId, Integer semester, String className);

    List<ClassTeacher> findByDepartmentIdOrderBySemesterAscClassNameAsc(Long departmentId);

    List<ClassTeacher> findAllByOrderByDepartmentIdAscSemesterAscClassNameAsc();
}
