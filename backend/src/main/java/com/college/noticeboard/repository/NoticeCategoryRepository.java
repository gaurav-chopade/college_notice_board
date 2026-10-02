package com.college.noticeboard.repository;

import com.college.noticeboard.entity.NoticeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoticeCategoryRepository extends JpaRepository<NoticeCategory, Long> {

    Optional<NoticeCategory> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<NoticeCategory> findByActiveTrueOrderByNameAsc();

    List<NoticeCategory> findAllByOrderByNameAsc();
}
