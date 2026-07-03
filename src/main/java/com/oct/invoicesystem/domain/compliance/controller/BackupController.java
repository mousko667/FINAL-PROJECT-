package com.oct.invoicesystem.domain.compliance.controller;

import com.oct.invoicesystem.domain.compliance.dto.ComplianceDTOs.BackupStatusResponse;
import com.oct.invoicesystem.domain.compliance.service.BackupService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/backups")
@RequiredArgsConstructor
@Tag(name = "Backups", description = "Gestion des sauvegardes (M14)")
public class BackupController {

    private final BackupService backupService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister les sauvegardes", description = "Liste les fichiers de sauvegarde disponibles.")
    public ResponseEntity<ApiResponse<List<String>>> listBackups() {
        return ResponseEntity.ok(ApiResponse.success(backupService.listBackups()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une sauvegarde", description = "Déclenche une sauvegarde de la base de données.")
    public ResponseEntity<ApiResponse<BackupStatusResponse>> createBackup() {
        return ResponseEntity.ok(ApiResponse.success(backupService.createBackup()));
    }

    @PostMapping("/{filename}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restaurer une sauvegarde", description = "Restaure le système à partir du fichier sélectionné.")
    public ResponseEntity<ApiResponse<BackupStatusResponse>> restoreBackup(@PathVariable String filename) {
        return ResponseEntity.ok(ApiResponse.success(backupService.restoreBackup(filename)));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historique d'audit des sauvegardes", description = "Retourne l'historique des opérations de sauvegarde et de restauration.")
    public ResponseEntity<ApiResponse<List<com.oct.invoicesystem.domain.compliance.dto.ComplianceDTOs.BackupAuditLogResponse>>> getAuditLogs() {
        return ResponseEntity.ok(ApiResponse.success(backupService.getAuditLogs()));
    }
}
