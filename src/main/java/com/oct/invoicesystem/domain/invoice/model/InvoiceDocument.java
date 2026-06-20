package com.oct.invoicesystem.domain.invoice.model;

import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "minio_object_key", nullable = false, unique = true, length = 500)
    private String minioObjectKey;

    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    // M9: versioning. version starts at 1; when a newer version is uploaded, the older row's
    // supersededByDocumentId points to the newer document (history preserved).
    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(name = "superseded_by_document_id")
    private UUID supersededByDocumentId;

    // M10 #10 refinement: disposition of a document past its retention horizon.
    @Enumerated(EnumType.STRING)
    @Column(name = "retention_disposition", nullable = false, length = 20)
    @Builder.Default
    private RetentionDisposition retentionDisposition = RetentionDisposition.PENDING;

    @Column(name = "retention_disposition_at")
    private Instant retentionDispositionAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retention_disposition_by")
    private User retentionDispositionBy;

    @PrePersist
    public void prePersist() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }
}
