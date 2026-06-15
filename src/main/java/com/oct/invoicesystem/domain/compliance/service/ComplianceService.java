package com.oct.invoicesystem.domain.compliance.service;

import com.oct.invoicesystem.domain.compliance.dto.ComplianceDTOs.*;
import com.oct.invoicesystem.domain.compliance.model.*;
import com.oct.invoicesystem.domain.compliance.repository.*;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Compliance & security extras (M14): incidents, checklist, calendar, privacy acceptance, backup status. */
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> FRAMEWORKS = Set.of("SOX", "IFRS", "LOCAL");

    @Value("${app.privacy.policyVersion:1.0}")
    private String currentPolicyVersion;

    private final SecurityIncidentRepository incidentRepository;
    private final ChecklistItemRepository checklistRepository;
    private final CalendarRepository calendarRepository;
    private final PrivacyAcceptanceRepository privacyRepository;
    private final BackupStatusRepository backupRepository;

    // ── Incidents ──
    @Transactional(readOnly = true)
    public List<IncidentResponse> listIncidents() {
        return incidentRepository.findByOrderByReportedAtDesc().stream().map(this::toIncident).toList();
    }

    @Transactional
    public IncidentResponse reportIncident(IncidentRequest req, UUID actorId) {
        String severity = req.severity() == null || req.severity().isBlank() ? "MEDIUM" : req.severity().toUpperCase();
        if (!SEVERITIES.contains(severity)) throw new ValidationException("Invalid severity: " + req.severity());
        SecurityIncident i = SecurityIncident.builder()
                .title(req.title()).description(req.description()).severity(severity)
                .status("OPEN").reportedBy(actorId).build();
        return toIncident(incidentRepository.save(i));
    }

    @Transactional
    public IncidentResponse setIncidentStatus(UUID id, String status) {
        SecurityIncident i = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
        String s = status == null ? "" : status.toUpperCase();
        if (!Set.of("OPEN", "INVESTIGATING", "RESOLVED", "CLOSED").contains(s)) {
            throw new ValidationException("Invalid status: " + status);
        }
        i.setStatus(s);
        if (s.equals("RESOLVED") || s.equals("CLOSED")) i.setResolvedAt(Instant.now());
        return toIncident(incidentRepository.save(i));
    }

    // ── Checklist ──
    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> listChecklist() {
        return checklistRepository.findByOrderByFrameworkAscLabelAsc().stream().map(this::toChecklist).toList();
    }

    @Transactional
    public ChecklistItemResponse addChecklistItem(ChecklistItemRequest req) {
        String framework = req.framework() == null ? "" : req.framework().toUpperCase();
        if (!FRAMEWORKS.contains(framework)) throw new ValidationException("Invalid framework (SOX|IFRS|LOCAL): " + req.framework());
        ComplianceChecklistItem item = ComplianceChecklistItem.builder()
                .framework(framework).label(req.label()).completed(false).build();
        return toChecklist(checklistRepository.save(item));
    }

    @Transactional
    public ChecklistItemResponse toggleChecklistItem(UUID id, boolean completed) {
        ComplianceChecklistItem item = checklistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found: " + id));
        item.setCompleted(completed);
        return toChecklist(checklistRepository.save(item));
    }

    @Transactional
    public void deleteChecklistItem(UUID id) {
        if (!checklistRepository.existsById(id)) throw new ResourceNotFoundException("Checklist item not found: " + id);
        checklistRepository.deleteById(id);
    }

    // ── Calendar ──
    @Transactional(readOnly = true)
    public List<CalendarResponse> listCalendar() {
        return calendarRepository.findByOrderByDueDateAsc().stream().map(this::toCalendar).toList();
    }

    @Transactional
    public CalendarResponse addCalendarEntry(CalendarRequest req) {
        ComplianceCalendarEntry e = ComplianceCalendarEntry.builder()
                .title(req.title()).dueDate(req.dueDate()).description(req.description()).completed(false).build();
        return toCalendar(calendarRepository.save(e));
    }

    @Transactional
    public CalendarResponse toggleCalendarEntry(UUID id, boolean completed) {
        ComplianceCalendarEntry e = calendarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar entry not found: " + id));
        e.setCompleted(completed);
        return toCalendar(calendarRepository.save(e));
    }

    @Transactional
    public void deleteCalendarEntry(UUID id) {
        if (!calendarRepository.existsById(id)) throw new ResourceNotFoundException("Calendar entry not found: " + id);
        calendarRepository.deleteById(id);
    }

    // ── Backup status ──
    @Transactional(readOnly = true)
    public BackupStatusResponse getBackupStatus() {
        BackupStatus b = backupRepository.findById(1).orElse(
                BackupStatus.builder().id(1).status("UNKNOWN").detail("No backup recorded yet.").build());
        return new BackupStatusResponse(b.getLastBackupAt(), b.getStatus(), b.getDetail());
    }

    @Transactional
    public BackupStatusResponse recordBackup(String status, String detail) {
        String s = status == null ? "OK" : status.toUpperCase();
        if (!Set.of("OK", "FAILED", "UNKNOWN").contains(s)) throw new ValidationException("Invalid backup status: " + status);
        BackupStatus b = backupRepository.findById(1).orElse(BackupStatus.builder().id(1).build());
        b.setStatus(s);
        b.setDetail(detail);
        b.setLastBackupAt(Instant.now());
        backupRepository.save(b);
        return new BackupStatusResponse(b.getLastBackupAt(), b.getStatus(), b.getDetail());
    }

    // ── Privacy policy acceptance ──
    @Transactional(readOnly = true)
    public PrivacyAcceptanceResponse getMyPrivacyAcceptance(UUID userId) {
        boolean accepted = privacyRepository.findByUserIdAndPolicyVersion(userId, currentPolicyVersion).isPresent();
        return new PrivacyAcceptanceResponse(accepted, currentPolicyVersion);
    }

    @Transactional
    public PrivacyAcceptanceResponse acceptPrivacy(UUID userId) {
        if (privacyRepository.findByUserIdAndPolicyVersion(userId, currentPolicyVersion).isEmpty()) {
            privacyRepository.save(PrivacyPolicyAcceptance.builder()
                    .userId(userId).policyVersion(currentPolicyVersion).build());
        }
        return new PrivacyAcceptanceResponse(true, currentPolicyVersion);
    }

    // ── mappers ──
    private IncidentResponse toIncident(SecurityIncident i) {
        return new IncidentResponse(i.getId(), i.getTitle(), i.getDescription(), i.getSeverity(),
                i.getStatus(), i.getReportedAt(), i.getResolvedAt());
    }
    private ChecklistItemResponse toChecklist(ComplianceChecklistItem c) {
        return new ChecklistItemResponse(c.getId(), c.getFramework(), c.getLabel(), c.isCompleted(), c.getNotes(), c.getUpdatedAt());
    }
    private CalendarResponse toCalendar(ComplianceCalendarEntry e) {
        return new CalendarResponse(e.getId(), e.getTitle(), e.getDueDate(), e.getDescription(), e.isCompleted());
    }
}
