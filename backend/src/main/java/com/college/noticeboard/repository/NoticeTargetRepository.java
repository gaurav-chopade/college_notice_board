package com.college.noticeboard.repository;

import com.college.noticeboard.entity.NoticeTarget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeTargetRepository extends JpaRepository<NoticeTarget, Long> {

    boolean existsByDepartmentId(Long departmentId);
}
