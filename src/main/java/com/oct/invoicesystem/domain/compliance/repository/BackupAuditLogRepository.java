package com.oct.invoicesystem.domain.compliance.repository;

import com.oct.invoicesystem.domain.compliance.model.BackupAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BackupAuditLogRepository extends JpaRepository<BackupAuditLog, UUID> {
    List<BackupAuditLog> findTop50ByOrderByCreatedAtDesc();
}
