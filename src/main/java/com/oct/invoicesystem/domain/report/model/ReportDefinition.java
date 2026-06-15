package com.oct.invoicesystem.domain.report.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A saved report definition for the custom builder + scheduled distribution (M11). */
@Entity
@Table(name = "report_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 40)
    private String dataset;   // INVOICES | SUPPLIERS | AUDIT | BUDGET

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String format = "CSV";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String frequency = "MANUAL"; // MANUAL | DAILY | WEEKLY | MONTHLY

    @Column(length = 2000)
    private String recipients; // comma-separated emails

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;
}
