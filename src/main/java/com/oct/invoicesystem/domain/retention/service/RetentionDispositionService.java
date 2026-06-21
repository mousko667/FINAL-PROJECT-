package com.oct.invoicesystem.domain.retention.service;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.retention.dto.RetentionPendingDocumentDTO;
import com.oct.invoicesystem.domain.retention.model.RetentionPolicy;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * ADMIN disposition of documents past their retention horizon (M10 #10 refinement).
 * Listing surfaces only expired documents still PENDING; setting a disposition (RETAINED/PURGED)
 * removes them from the count so the compliance card stops flagging ATTENTION once handled.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RetentionDispositionService {

    private final InvoiceDocumentRepository invoiceDocumentRepository;
    private final RetentionPolicyService retentionPolicyService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RetentionPendingDocumentDTO> listPendingExpired() {
        RetentionPolicy policy = retentionPolicyService.getEntity();
        Instant cutoff = Instant.now().minus(policy.getRetentionYears() * 365L, ChronoUnit.DAYS);
        return invoiceDocumentRepository
                .findByUploadedAtBeforeAndRetentionDisposition(cutoff, RetentionDisposition.PENDING)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public RetentionPendingDocumentDTO setDisposition(UUID docId, RetentionDisposition target, User actor) {
        if (target == RetentionDisposition.PENDING) {
            throw new ValidationException("retention.disposition.invalid_target");
        }
        InvoiceDocument doc = invoiceDocumentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id " + docId));

        RetentionDisposition previous = doc.getRetentionDisposition();
        doc.setRetentionDisposition(target);
        doc.setRetentionDispositionAt(Instant.now());
        doc.setRetentionDispositionBy(actor);
        InvoiceDocument saved = invoiceDocumentRepository.save(doc);

        auditService.logAction(actor != null ? actor.getId() : null, "INVOICE_DOCUMENT",
                docId.toString(), "RETENTION_DISPOSITION",
                previous != null ? previous.name() : null, target.name(), null, null);

        return toDto(saved);
    }

    private RetentionPendingDocumentDTO toDto(InvoiceDocument d) {
        return new RetentionPendingDocumentDTO(
                d.getId(),
                d.getInvoice() != null ? d.getInvoice().getId() : null,
                d.getOriginalFilename(),
                d.getUploadedAt(),
                d.getRetentionDisposition());
    }
}
