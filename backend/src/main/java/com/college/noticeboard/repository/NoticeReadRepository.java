package com.college.noticeboard.repository;

import com.college.noticeboard.entity.NoticeRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NoticeReadRepository extends JpaRepository<NoticeRead, Long> {

    boolean existsByNoticeIdAndUserId(Long noticeId, Long userId);

    @Query("select r.user.id from NoticeRead r where r.notice.id = :noticeId")
    List<Long> findUserIdsByNoticeId(@Param("noticeId") Long noticeId);

    @Query("select r.notice.id from NoticeRead r where r.user.id = :userId and r.notice.id in :noticeIds")
    List<Long> findReadNoticeIds(@Param("userId") Long userId, @Param("noticeIds") Collection<Long> noticeIds);

    @Modifying
    @Query("delete from NoticeRead r where r.notice.id = :noticeId")
    int deleteByNoticeId(@Param("noticeId") Long noticeId);
}
