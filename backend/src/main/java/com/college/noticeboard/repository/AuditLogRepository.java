package com.college.noticeboard.repository;

import com.college.noticeboard.entity.AuditAction;
import com.college.noticeboard.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Lifecycle history of one entity, oldest first (used for the notice approval timeline). */
    List<AuditLog> findByEntityTypeAndEntityIdAndActionInOrderByTimestampAscIdAsc(
            String entityType, Long entityId, Collection<AuditAction> actions);
}
