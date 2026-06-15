package com.oct.invoicesystem.domain.compliance.controller;

import com.oct.invoicesystem.domain.compliance.dto.ComplianceDTOs.*;
import com.oct.invoicesystem.domain.compliance.service.ComplianceService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Compliance & security extras (M14). */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance", description = "Incidents, checklist, calendar, backup, privacy acceptance")
public class ComplianceController {

    private final ComplianceService service;
    private final SecurityHelper securityHelper;

    // ── Incidents (report = any staff non-supplier; manage = ADMIN) ──
    @GetMapping("/incidents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<IncidentResponse>>> listIncidents() {
        return ResponseEntity.ok(ApiResponse.success(service.listIncidents()));
    }

    @PostMapping("/incidents")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> reportIncident(
            @Valid @RequestBody IncidentRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                service.reportIncident(req, securityHelper.currentUserId(auth)), "Incident reported"));
    }

    @PatchMapping("/incidents/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<IncidentResponse>> setIncidentStatus(
            @PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(service.setIncidentStatus(id, status), "Incident updated"));
    }

    // ── SOX/IFRS checklist (ADMIN) ──
    @GetMapping("/checklist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ChecklistItemResponse>>> listChecklist() {
        return ResponseEntity.ok(ApiResponse.success(service.listChecklist()));
    }

    @PostMapping("/checklist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> addChecklist(@Valid @RequestBody ChecklistItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.addChecklistItem(req), "Checklist item added"));
    }

    @PatchMapping("/checklist/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> toggleChecklist(
            @PathVariable UUID id, @RequestParam boolean completed) {
        return ResponseEntity.ok(ApiResponse.success(service.toggleChecklistItem(id, completed), "Checklist updated"));
    }

    @DeleteMapping("/checklist/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteChecklist(@PathVariable UUID id) {
        service.deleteChecklistItem(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Checklist item deleted"));
    }

    // ── Compliance calendar (ADMIN) ──
    @GetMapping("/calendar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CalendarResponse>>> listCalendar() {
        return ResponseEntity.ok(ApiResponse.success(service.listCalendar()));
    }

    @PostMapping("/calendar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CalendarResponse>> addCalendar(@Valid @RequestBody CalendarRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.addCalendarEntry(req), "Calendar entry added"));
    }

    @PatchMapping("/calendar/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CalendarResponse>> toggleCalendar(
            @PathVariable UUID id, @RequestParam boolean completed) {
        return ResponseEntity.ok(ApiResponse.success(service.toggleCalendarEntry(id, completed), "Calendar updated"));
    }

    @DeleteMapping("/calendar/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCalendar(@PathVariable UUID id) {
        service.deleteCalendarEntry(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Calendar entry deleted"));
    }

    // ── Backup status (read ADMIN; record ADMIN) ──
    @GetMapping("/backup-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BackupStatusResponse>> backupStatus() {
        return ResponseEntity.ok(ApiResponse.success(service.getBackupStatus()));
    }

    @PostMapping("/backup-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BackupStatusResponse>> recordBackup(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                service.recordBackup(body.getOrDefault("status", "OK"), body.get("detail")), "Backup recorded"));
    }

    // ── Privacy-policy acceptance (any authenticated user) ──
    @GetMapping("/privacy-acceptance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PrivacyAcceptanceResponse>> myPrivacy(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.getMyPrivacyAcceptance(securityHelper.currentUserId(auth))));
    }

    @PostMapping("/privacy-acceptance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PrivacyAcceptanceResponse>> acceptPrivacy(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                service.acceptPrivacy(securityHelper.currentUserId(auth)), "Privacy policy accepted"));
    }
}
