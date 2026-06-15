package com.oct.invoicesystem.domain.audit.repository;

import com.oct.invoicesystem.domain.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.UUID;

// Append-only repository by extending Repository instead of JpaRepository/CrudRepository
public interface AuditLogRepository extends Repository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    AuditLog save(AuditLog entity);

    Page<AuditLog> findAll(Pageable pageable);

    // M10 anomaly detection — per-user activity counts since a cut-off, and access-denied counts.
    @org.springframework.data.jpa.repository.Query("""
        SELECT a.user.id, COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :since AND a.user IS NOT NULL
        GROUP BY a.user.id
    """)
    java.util.List<Object[]> countByUserSince(java.time.Instant since);

    @org.springframework.data.jpa.repository.Query("""
        SELECT a.user.id, COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :since AND a.user IS NOT NULL AND a.action = :action
        GROUP BY a.user.id
    """)
    java.util.List<Object[]> countByUserSinceAndAction(java.time.Instant since, String action);
}
