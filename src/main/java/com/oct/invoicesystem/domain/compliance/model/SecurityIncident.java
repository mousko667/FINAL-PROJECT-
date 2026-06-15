package com.oct.invoicesystem.domain.compliance.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A reported security incident (M14). */
@Entity
@Table(name = "security_incidents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityIncident {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severity = "MEDIUM";   // LOW | MEDIUM | HIGH | CRITICAL

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";       // OPEN | INVESTIGATING | RESOLVED | CLOSED

    @Column(name = "reported_by")
    private UUID reportedBy;

    @CreationTimestamp
    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
