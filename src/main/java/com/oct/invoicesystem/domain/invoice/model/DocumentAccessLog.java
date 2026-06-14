package com.oct.invoicesystem.domain.invoice.model;

import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of an access to an invoice document (P11-50 / REQ-16, partial scope).
 * One row is written each time a download URL is generated. Never updated or deleted
 * (enforced at the DB level by triggers, migration V48).
 */
@Entity
@Table(name = "document_access_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private InvoiceDocument document;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accessed_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User accessedBy;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String action = "DOWNLOAD";

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "accessed_at", nullable = false, updatable = false)
    private Instant accessedAt;
}
