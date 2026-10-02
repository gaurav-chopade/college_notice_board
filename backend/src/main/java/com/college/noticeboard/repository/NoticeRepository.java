package com.college.noticeboard.repository;

import com.college.noticeboard.entity.Notice;
import com.college.noticeboard.entity.NoticeStatus;
import com.college.noticeboard.entity.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long>, JpaSpecificationExecutor<Notice> {

    @Override
    @EntityGraph(attributePaths = {"category", "createdBy", "department"})
    Page<Notice> findAll(Specification<Notice> spec, Pageable pageable);

    List<Notice> findByStatusAndPublishDateLessThanEqual(NoticeStatus status, LocalDateTime now);

    List<Notice> findByStatusAndExpiryDateLessThanEqual(NoticeStatus status, LocalDateTime now);

    long countByStatus(NoticeStatus status);

    long countByCreatedById(Long userId);

    long countByCreatedByIdAndStatus(Long userId, NoticeStatus status);

    long countByCreatedByIdAndPriority(Long userId, Priority priority);

    long countByCreatedByIdAndReviewedByIsNotNullAndStatusNot(Long userId, NoticeStatus status);

    long countByDepartmentId(Long departmentId);

    long countByDepartmentIdAndStatus(Long departmentId, NoticeStatus status);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByDepartmentId(Long departmentId);

    @Query("select n.department.name, count(n) from Notice n where n.department is not null group by n.department.name")
    List<Object[]> countGroupedByDepartment();

    @Query("select n.id from Notice n where n.createdBy.id = :userId and n.status in :statuses")
    List<Long> findIdsByCreatorAndStatusIn(@Param("userId") Long userId, @Param("statuses") List<NoticeStatus> statuses);
}
