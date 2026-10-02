package com.college.noticeboard.repository;

import com.college.noticeboard.entity.Department;
import com.college.noticeboard.entity.InstitutionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndInstitutionType(String name, InstitutionType institutionType);

    boolean existsByCodeIgnoreCase(String code);

    Optional<Department> findByCodeIgnoreCase(String code);
}
