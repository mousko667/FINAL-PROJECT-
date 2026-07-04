package com.oct.invoicesystem.domain.compliance.service;

import com.oct.invoicesystem.domain.compliance.dto.ComplianceDTOs.BackupAuditLogResponse;
import com.oct.invoicesystem.domain.compliance.dto.ComplianceDTOs.BackupStatusResponse;
import com.oct.invoicesystem.domain.compliance.repository.BackupAuditLogRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class BackupServiceIntegrationTest {

    @Autowired
    private BackupService backupService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private MinioStorageService minioStorageService;

    @Autowired
    private BackupAuditLogRepository auditLogRepository;

    private List<String> mockBackups;

    @BeforeEach
    void setup() throws Exception {
        mockBackups = new java.util.ArrayList<>();
        
        org.mockito.Mockito.when(minioStorageService.listObjects(org.mockito.Mockito.anyString())).thenReturn(mockBackups);
        
        org.mockito.Mockito.doAnswer(invocation -> {
            String filename = invocation.getArgument(0);
            mockBackups.add(filename);
            return null;
        }).when(minioStorageService).upload(org.mockito.Mockito.anyString(), org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
        
        org.mockito.Mockito.doAnswer(invocation -> {
            String filename = invocation.getArgument(0);
            mockBackups.remove(filename);
            return null;
        }).when(minioStorageService).delete(org.mockito.Mockito.anyString());
        
        auditLogRepository.deleteAll();
    }

    @AfterEach
    void tearDown() throws Exception {
        auditLogRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCompleteBackupCycle() {
        // 1. Create a backup
        BackupStatusResponse createResponse = backupService.createBackup();
        assertEquals("OK", createResponse.status());
        assertTrue(createResponse.detail().contains("Backup completed"));

        // 2. List backups
        List<String> backups = backupService.listBackups();
        assertEquals(1, backups.size());
        String filename = backups.get(0);

        // 3. Verify audit log for CREATE
        List<BackupAuditLogResponse> logs = backupService.getAuditLogs();
        assertEquals(1, logs.size());
        assertEquals("CREATE", logs.get(0).operation());
        assertEquals("OK", logs.get(0).status());
        assertTrue(logs.get(0).filename().contains(filename));
        assertEquals("admin", logs.get(0).triggeredBy());

        // 4. Restore backup
        BackupStatusResponse restoreResponse = backupService.restoreBackup(filename);
        assertEquals("OK", restoreResponse.status());

        // 5. Verify audit log for RESTORE
        logs = backupService.getAuditLogs();
        assertEquals(2, logs.size());
        assertEquals("RESTORE", logs.get(0).operation()); // It's ordered descending by date
        assertEquals("OK", logs.get(0).status());
        assertEquals("admin", logs.get(0).triggeredBy());
    }

    @Test
    void testPurgeOldBackups() {
        // Set retention count to 1 for this test
        ReflectionTestUtils.setField(backupService, "retentionCount", 1);

        // Create 3 backups
        backupService.createBackup();
        backupService.createBackup();
        backupService.createBackup();

        List<String> backupsBefore = backupService.listBackups();
        assertEquals(3, backupsBefore.size());

        // Purge
        backupService.purgeOldBackups("SYSTEM");

        List<String> backupsAfter = backupService.listBackups();
        assertEquals(1, backupsAfter.size(), "Should retain only 1 backup");

        // The remaining one should be the most recent one (first in the list before purge)
        assertEquals(backupsBefore.get(0), backupsAfter.get(0));

        // Check audit logs for PURGE
        List<BackupAuditLogResponse> logs = backupService.getAuditLogs();
        long purgeCount = logs.stream().filter(l -> l.operation().equals("PURGE")).count();
        assertEquals(2, purgeCount, "Should have 2 PURGE audit logs");
    }

    @Test
    void testScheduledBackup() {
        ReflectionTestUtils.setField(backupService, "retentionCount", 7);
        
        backupService.runScheduledBackup();
        
        List<String> backups = backupService.listBackups();
        assertEquals(1, backups.size());
        
        List<BackupAuditLogResponse> logs = backupService.getAuditLogs();
        // Since there is only 1 backup, purge won't delete anything, so we only expect CREATE
        assertEquals(1, logs.size());
        assertEquals("CREATE", logs.get(0).operation());
        assertEquals("SYSTEM", logs.get(0).triggeredBy());
    }

    // --- B-2: path traversal sanitization on restoreBackup(filename) ---

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void restoreBackup_rejectsPathTraversal_dotdotSlash() throws Exception {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> backupService.restoreBackup("../../etc/passwd"));
        assertEquals("error.backup.invalid_filename", ex.getMessage());
        verify(minioStorageService, never()).download(anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void restoreBackup_rejectsPathTraversal_nestedSlash() throws Exception {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> backupService.restoreBackup("a/b"));
        assertEquals("error.backup.invalid_filename", ex.getMessage());
        verify(minioStorageService, never()).download(anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void restoreBackup_rejectsPathTraversal_backslash() throws Exception {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> backupService.restoreBackup("..\\..\\x"));
        assertEquals("error.backup.invalid_filename", ex.getMessage());
        verify(minioStorageService, never()).download(anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void restoreBackup_rejectsNullFilename() throws Exception {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> backupService.restoreBackup(null));
        assertEquals("error.backup.invalid_filename", ex.getMessage());
        verify(minioStorageService, never()).download(anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void restoreBackup_acceptsValidFilename_pastesValidationAndAttemptsDownload() throws Exception {
        String validFilename = "backup-20260704.sql.gz";

        // Must not throw the filename-validation exception; it may still proceed to
        // attempt (and mock-succeed at) the download step.
        assertDoesNotThrow(() -> backupService.restoreBackup(validFilename));

        verify(minioStorageService).download("backups/" + validFilename);
    }
}
