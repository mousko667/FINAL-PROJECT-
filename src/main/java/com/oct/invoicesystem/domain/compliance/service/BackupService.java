package com.oct.invoicesystem.domain.compliance.service;

import com.oct.invoicesystem.domain.compliance.dto.ComplianceDTOs.BackupAuditLogResponse;
import com.oct.invoicesystem.domain.compliance.dto.ComplianceDTOs.BackupStatusResponse;
import com.oct.invoicesystem.domain.compliance.model.BackupAuditLog;
import com.oct.invoicesystem.domain.compliance.repository.BackupAuditLogRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service pour la sauvegarde et restauration (M14).
 * En mode projet de fin d'études, simule la création et la restauration de fichiers dump SQL
 * via MinIO pour démontrer le fonctionnement sans casser la DB locale de l'application.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final MinioStorageService minioStorageService;
    private final ComplianceService complianceService;
    private final BackupAuditLogRepository backupAuditLogRepository;

    @Value("${app.backup.retention.count:7}")
    private int retentionCount;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("UTC"));

    @Scheduled(cron = "${app.backup.schedule.cron:0 0 2 * * *}")
    @Transactional
    public void runScheduledBackup() {
        log.info("Starting scheduled automated backup...");
        createBackupInternal("SYSTEM");
        purgeOldBackups("SYSTEM");
    }

    @Transactional
    public BackupStatusResponse createBackup() {
        return createBackupInternal(getCurrentUser());
    }

    private BackupStatusResponse createBackupInternal(String triggeredBy) {
        String filename = "backups/backup-" + FORMATTER.format(Instant.now()) + ".sql.gz";
        try {
            String dummyContent = "-- Mock PostgreSQL Backup for OCT Invoice System\n-- Generated at: " + Instant.now();
            minioStorageService.upload(filename, dummyContent.getBytes(StandardCharsets.UTF_8), "application/gzip");
            log.info("Backup simulated and uploaded: {}", filename);
            recordAudit("CREATE", filename, "OK", null, triggeredBy);
            return complianceService.recordBackup("OK", "Backup completed: " + filename);
        } catch (Exception e) {
            log.error("Backup failed", e);
            recordAudit("CREATE", filename, "FAILED", e.getMessage(), triggeredBy);
            return complianceService.recordBackup("FAILED", "Backup failed: " + e.getMessage());
        }
    }

    public List<String> listBackups() {
        try {
            return minioStorageService.listObjects("backups/").stream()
                    .map(name -> name.replace("backups/", ""))
                    .sorted((a, b) -> b.compareTo(a)) // sort descending
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to list backups", e);
            return List.of();
        }
    }

    @Transactional
    public BackupStatusResponse restoreBackup(String filename) {
        // Must be validated BEFORE the try block below: that block catches(Exception)
        // and swallows any exception into a "FAILED" success response (200), which
        // would defeat a path-traversal rejection if thrown from inside it.
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+")) {
            throw new ValidationException("error.backup.invalid_filename");
        }
        String triggeredBy = getCurrentUser();
        try {
            // Validate the file exists
            String fullPath = "backups/" + filename;
            minioStorageService.download(fullPath); // Throws if not found
            
            // Simulate restore process taking a moment
            Thread.sleep(1000); 

            log.info("Restore simulated from: {}", filename);
            recordAudit("RESTORE", filename, "OK", null, triggeredBy);
            // In a real scenario, this would call pg_restore. For the FYP, we simulate success and update the status.
            return complianceService.recordBackup("OK", "System restored from: " + filename);
        } catch (Exception e) {
            log.error("Restore failed for " + filename, e);
            recordAudit("RESTORE", filename, "FAILED", e.getMessage(), triggeredBy);
            return complianceService.recordBackup("FAILED", "Restore failed: " + e.getMessage());
        }
    }

    @Transactional
    public void purgeOldBackups(String triggeredBy) {
        try {
            List<String> allBackups = listBackups();
            if (allBackups.size() > retentionCount) {
                List<String> toDelete = allBackups.subList(retentionCount, allBackups.size());
                for (String filename : toDelete) {
                    try {
                        minioStorageService.delete("backups/" + filename);
                        recordAudit("PURGE", filename, "OK", null, triggeredBy);
                        log.info("Purged old backup: {}", filename);
                    } catch (Exception e) {
                        log.error("Failed to purge old backup: {}", filename, e);
                        recordAudit("PURGE", filename, "FAILED", e.getMessage(), triggeredBy);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute purge logic", e);
        }
    }

    @Transactional(readOnly = true)
    public List<BackupAuditLogResponse> getAuditLogs() {
        return backupAuditLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(log -> new BackupAuditLogResponse(
                        log.getId(),
                        log.getOperation(),
                        log.getFilename(),
                        log.getStatus(),
                        log.getErrorMessage(),
                        log.getTriggeredBy(),
                        log.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    private void recordAudit(String operation, String filename, String status, String error, String triggeredBy) {
        BackupAuditLog audit = BackupAuditLog.builder()
                .operation(operation)
                .filename(filename)
                .status(status)
                .errorMessage(error)
                .triggeredBy(triggeredBy)
                .build();
        backupAuditLogRepository.save(audit);
    }

    private String getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            return authentication.getName();
        }
        return "SYSTEM";
    }
}

