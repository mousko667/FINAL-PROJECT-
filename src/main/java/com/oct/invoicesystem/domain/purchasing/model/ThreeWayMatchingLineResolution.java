package com.oct.invoicesystem.domain.purchasing.model;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "three_way_matching_line_resolutions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"invoice_id", "po_line_id"})
})
// PROB-081: financial audit trail — append-only. Hibernate ignores any UPDATE on this entity;
// the DB also rejects UPDATE/DELETE via a trigger (V41) for defence in depth.
@Immutable
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreeWayMatchingLineResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_line_id", nullable = false)
    private PurchaseOrderItem poLine;

    @Column(nullable = false, length = 50)
    private String status; // RESOLVED

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resolved_by", nullable = false)
    private User resolvedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
