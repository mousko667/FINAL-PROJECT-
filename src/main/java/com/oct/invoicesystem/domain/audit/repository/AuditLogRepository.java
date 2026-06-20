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

    // M10 #12 — aggregated summary report queries (role-scoped via allowedActions IN-clause).
    @org.springframework.data.jpa.repository.Query("""
        SELECT a.action, COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :from AND a.createdAt < :to AND a.action IN :allowedActions
        GROUP BY a.action ORDER BY COUNT(a) DESC
    """)
    java.util.List<Object[]> summaryByAction(java.time.Instant from, java.time.Instant to, java.util.List<String> allowedActions);

    @org.springframework.data.jpa.repository.Query("""
        SELECT a.entityType, COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :from AND a.createdAt < :to AND a.action IN :allowedActions
        GROUP BY a.entityType ORDER BY COUNT(a) DESC
    """)
    java.util.List<Object[]> summaryByEntityType(java.time.Instant from, java.time.Instant to, java.util.List<String> allowedActions);

    @org.springframework.data.jpa.repository.Query("""
        SELECT a.user.username, COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :from AND a.createdAt < :to AND a.action IN :allowedActions
        GROUP BY a.user.username ORDER BY COUNT(a) DESC
    """)
    java.util.List<Object[]> summaryByUser(java.time.Instant from, java.time.Instant to, java.util.List<String> allowedActions);

    @org.springframework.data.jpa.repository.Query("""
        SELECT CAST(a.createdAt AS date), COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :from AND a.createdAt < :to AND a.action IN :allowedActions
        GROUP BY CAST(a.createdAt AS date) ORDER BY CAST(a.createdAt AS date) ASC
    """)
    java.util.List<Object[]> summaryByDay(java.time.Instant from, java.time.Instant to, java.util.List<String> allowedActions);
}
