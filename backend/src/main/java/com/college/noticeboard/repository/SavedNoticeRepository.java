package com.college.noticeboard.repository;

import com.college.noticeboard.entity.SavedNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SavedNoticeRepository extends JpaRepository<SavedNotice, Long> {

    boolean existsByNoticeIdAndUserId(Long noticeId, Long userId);

    @Query("select s.notice.id from SavedNotice s where s.user.id = :userId and s.notice.id in :noticeIds")
    List<Long> findSavedNoticeIds(@Param("userId") Long userId, @Param("noticeIds") Collection<Long> noticeIds);

    @Modifying
    @Query("delete from SavedNotice s where s.notice.id = :noticeId and s.user.id = :userId")
    int deleteByNoticeIdAndUserId(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    @Modifying
    @Query("delete from SavedNotice s where s.notice.id = :noticeId")
    int deleteByNoticeId(@Param("noticeId") Long noticeId);
}
