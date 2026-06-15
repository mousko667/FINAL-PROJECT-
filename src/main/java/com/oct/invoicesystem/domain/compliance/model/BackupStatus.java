package com.oct.invoicesystem.domain.compliance.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Singleton backup-status row (M14). Updated by the backup process or an admin. */
@Entity
@Table(name = "backup_status")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BackupStatus {
    @Id
    @Builder.Default
    private Integer id = 1;

    @Column(name = "last_backup_at")
    private Instant lastBackupAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "UNKNOWN";  // OK | FAILED | UNKNOWN

    @Column(length = 1000)
    private String detail;
}
