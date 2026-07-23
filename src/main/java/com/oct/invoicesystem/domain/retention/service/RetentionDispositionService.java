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
        // AUDIT-009: proposed purges must stay visible, otherwise the DAF has nothing to confirm.
        return java.util.stream.Stream.of(RetentionDisposition.PENDING, RetentionDisposition.PURGE_PROPOSED)
                .flatMap(d -> invoiceDocumentRepository
                        .findByUploadedAtBeforeAndRetentionDisposition(cutoff, d).stream())
                .map(this::toDto)
                .toList();
    }

    /**
     * Records a retention disposition, enforcing the two-man rule on destruction (AUDIT-009, D5).
     *
     * <p>{@code RETAINED} and {@code PURGE_PROPOSED} are ADMIN decisions. {@code PURGED} — the one
     * that condemns a financial supporting document — is reserved to the DAF, and only on a document
     * the ADMIN has already moved to {@code PURGE_PROPOSED}: proposer and confirmer are therefore
     * always two distinct roles.</p>
     *
     * @param docId  document to dispose of
     * @param target requested disposition
     * @param actor  authenticated decision maker
     * @return the document's new disposition state
     * @throws ValidationException when the target or the current state forbids the transition
     */
    public RetentionPendingDocumentDTO setDisposition(UUID docId, RetentionDisposition target, User actor) {
        if (target == RetentionDisposition.PENDING) {
            throw new ValidationException("retention.disposition.invalid_target");
        }
        InvoiceDocument doc = invoiceDocumentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id " + docId));

        RetentionDisposition previous = doc.getRetentionDisposition();
        boolean isDaf = hasRole(actor, "ROLE_DAF");
        if (target == RetentionDisposition.PURGED) {
            // Second man: only the DAF confirms, and only what the ADMIN has proposed.
            if (!isDaf) {
                throw new ValidationException("retention.disposition.purge_requires_daf");
            }
            if (previous != RetentionDisposition.PURGE_PROPOSED) {
                throw new ValidationException("retention.disposition.purge_not_proposed");
            }
        } else if (isDaf && !hasRole(actor, "ROLE_ADMIN")) {
            // First man: proposing (or retaining) stays an ADMIN act, so the DAF cannot do both.
            throw new ValidationException("retention.disposition.proposal_requires_admin");
        }

        doc.setRetentionDisposition(target);
        doc.setRetentionDispositionAt(Instant.now());
        doc.setRetentionDispositionBy(actor);
        InvoiceDocument saved = invoiceDocumentRepository.save(doc);

        auditService.logAction(actor != null ? actor.getId() : null, "INVOICE_DOCUMENT",
                docId.toString(), "RETENTION_DISPOSITION",
                previous != null ? previous.name() : null, target.name(), null, null);

        return toDto(saved);
    }

    private boolean hasRole(User user, String role) {
        return user != null && user.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
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
