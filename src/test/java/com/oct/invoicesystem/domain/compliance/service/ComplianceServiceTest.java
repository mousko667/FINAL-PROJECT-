package com.oct.invoicesystem.domain.compliance.service;

import com.oct.invoicesystem.domain.compliance.dto.ComplianceDTOs.*;
import com.oct.invoicesystem.domain.compliance.model.*;
import com.oct.invoicesystem.domain.compliance.repository.*;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceServiceTest {

    @Mock private SecurityIncidentRepository incidentRepository;
    @Mock private ChecklistItemRepository checklistRepository;
    @Mock private CalendarRepository calendarRepository;
    @Mock private PrivacyAcceptanceRepository privacyRepository;
    @Mock private BackupStatusRepository backupRepository;

    private ComplianceService service;

    @BeforeEach
    void setUp() {
        service = new ComplianceService(incidentRepository, checklistRepository, calendarRepository,
                privacyRepository, backupRepository);
        ReflectionTestUtils.setField(service, "currentPolicyVersion", "1.0");
    }

    @Test
    void reportIncident_defaultsSeverityMedium() {
        when(incidentRepository.save(any(SecurityIncident.class))).thenAnswer(i -> i.getArgument(0));
        IncidentResponse r = service.reportIncident(new IncidentRequest("Phishing email", "details", null), UUID.randomUUID());
        assertEquals("MEDIUM", r.severity());
        assertEquals("OPEN", r.status());
    }

    @Test
    void reportIncident_rejectsBadSeverity() {
        assertThrows(ValidationException.class,
                () -> service.reportIncident(new IncidentRequest("x", null, "FATAL"), UUID.randomUUID()));
    }

    @Test
    void addChecklistItem_rejectsBadFramework() {
        assertThrows(ValidationException.class,
                () -> service.addChecklistItem(new ChecklistItemRequest("PCI", "Encrypt cards")));
    }

    @Test
    void acceptPrivacy_recordsWhenAbsent() {
        UUID userId = UUID.randomUUID();
        when(privacyRepository.findByUserIdAndPolicyVersion(userId, "1.0")).thenReturn(Optional.empty());
        when(privacyRepository.save(any(PrivacyPolicyAcceptance.class))).thenAnswer(i -> i.getArgument(0));
        PrivacyAcceptanceResponse r = service.acceptPrivacy(userId);
        assertTrue(r.accepted());
        assertEquals("1.0", r.policyVersion());
    }

    @Test
    void recordBackup_setsStatusAndTimestamp() {
        when(backupRepository.findById(1)).thenReturn(Optional.of(BackupStatus.builder().id(1).build()));
        when(backupRepository.save(any(BackupStatus.class))).thenAnswer(i -> i.getArgument(0));
        BackupStatusResponse r = service.recordBackup("OK", "nightly dump");
        assertEquals("OK", r.status());
        assertNotNull(r.lastBackupAt());
    }
}
