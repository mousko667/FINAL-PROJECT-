package com.oct.invoicesystem.domain.webhook.model;

import com.oct.invoicesystem.shared.util.EncryptionAttributeConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A configurable integration connector (M12): ERP / accounting / banking / DMS / MOCK. */
@Entity
@Table(name = "integration_connectors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationConnector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 30)
    private String type;     // ERP | ACCOUNTING | BANKING | DMS | MOCK

    @Column(length = 500)
    private String endpoint;

    @Convert(converter = EncryptionAttributeConverter.class)
    @Column(name = "config", columnDefinition = "TEXT")
    private String config;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "last_status", length = 20)
    private String lastStatus;   // UP | DOWN | UNKNOWN

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_message", length = 1000)
    private String lastMessage;

    /** Scheduled-sync interval in minutes; null = automatic sync disabled (B6, M12 #10). */
    @Column(name = "sync_interval_minutes")
    private Integer syncIntervalMinutes;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_sync_status", length = 20)
    private String lastSyncStatus;   // SUCCESS | FAILED

    @Column(name = "last_sync_message", length = 1000)
    private String lastSyncMessage;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
