package com.college.noticeboard.repository;

import com.college.noticeboard.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByNoticeIdOrderByUploadedAtAsc(Long noticeId);

    long countByNoticeId(Long noticeId);

    @Query("select a.notice.id, count(a) from Attachment a where a.notice.id in :ids group by a.notice.id")
    List<Object[]> countByNoticeIds(@Param("ids") Collection<Long> ids);
}
