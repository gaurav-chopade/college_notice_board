package com.college.noticeboard.repository;

import com.college.noticeboard.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    long countByNoticeId(Long noticeId);

    @Modifying
    @Query("update Notification n set n.isRead = true where n.user.id = :userId and n.isRead = false")
    int markAllRead(@Param("userId") Long userId);

    @Modifying
    @Query("update Notification n set n.isRead = true where n.user.id = :userId and n.notice.id = :noticeId")
    int markReadForNotice(@Param("userId") Long userId, @Param("noticeId") Long noticeId);

    @Modifying
    @Query("delete from Notification n where n.notice.id = :noticeId")
    int deleteByNoticeId(@Param("noticeId") Long noticeId);
}
