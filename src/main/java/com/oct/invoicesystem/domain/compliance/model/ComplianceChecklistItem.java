package com.oct.invoicesystem.domain.compliance.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A compliance checklist item for a framework (SOX/IFRS/LOCAL) (M14). */
@Entity
@Table(name = "compliance_checklist_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplianceChecklistItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String framework;   // SOX | IFRS | LOCAL

    @Column(nullable = false, length = 500)
    private String label;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Column(length = 2000)
    private String notes;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
