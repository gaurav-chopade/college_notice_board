package com.college.noticeboard.repository;

import com.college.noticeboard.entity.NoticeAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoticeAcknowledgementRepository extends JpaRepository<NoticeAcknowledgement, Long> {

    boolean existsByNoticeIdAndUserId(Long noticeId, Long userId);

    @Query("select a.user.id from NoticeAcknowledgement a where a.notice.id = :noticeId")
    List<Long> findUserIdsByNoticeId(@Param("noticeId") Long noticeId);

    @Modifying
    @Query("delete from NoticeAcknowledgement a where a.notice.id = :noticeId")
    int deleteByNoticeId(@Param("noticeId") Long noticeId);
}
