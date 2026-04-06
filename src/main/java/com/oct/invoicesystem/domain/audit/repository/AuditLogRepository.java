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
}
