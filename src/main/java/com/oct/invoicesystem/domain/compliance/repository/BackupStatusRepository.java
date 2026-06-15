package com.oct.invoicesystem.domain.compliance.repository;

import com.oct.invoicesystem.domain.compliance.model.BackupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BackupStatusRepository extends JpaRepository<BackupStatus, Integer> {
}
