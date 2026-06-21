package com.oct.invoicesystem.domain.invoice.repository;

import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceDocumentRepository extends JpaRepository<InvoiceDocument, UUID> {
    List<InvoiceDocument> findByInvoiceId(UUID invoiceId);
    Optional<InvoiceDocument> findByIdAndInvoiceId(UUID id, UUID invoiceId);

    // M9 versioning: the current (non-superseded) document with this filename on this invoice, if any.
    Optional<InvoiceDocument> findFirstByInvoiceIdAndOriginalFilenameAndSupersededByDocumentIdIsNull(
            UUID invoiceId, String originalFilename);

    // M9 retention: documents uploaded on/before a cut-off (for the retention sweep).
    List<InvoiceDocument> findByUploadedAtBefore(java.time.Instant cutoff);

    // M10 #10 refinement: expired documents still awaiting a disposition decision (retention sweep).
    List<InvoiceDocument> findByUploadedAtBeforeAndRetentionDisposition(
            java.time.Instant cutoff, RetentionDisposition disposition);
}
