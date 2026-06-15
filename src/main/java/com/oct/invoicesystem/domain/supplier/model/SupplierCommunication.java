package com.oct.invoicesystem.domain.supplier.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A logged communication with a supplier (M8). */
@Entity
@Table(name = "supplier_communications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierCommunication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String channel = "NOTE";

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(length = 2000)
    private String body;

    @Column(name = "logged_by")
    private UUID loggedBy;

    @CreationTimestamp
    @Column(name = "logged_at", nullable = false, updatable = false)
    private Instant loggedAt;
}
