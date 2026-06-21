package com.oct.invoicesystem.domain.invoice.scheduler;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.retention.model.RetentionPolicy;
import com.oct.invoicesystem.domain.retention.service.RetentionPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * M9 retention policy enforcement (non-destructive). Once a day, flags invoice documents older than
 * the configured retention period and records a {@code RETENTION_FLAG} audit entry per document.
 *
 * <p>The retention period and the on/off switch are read at runtime from the DB-backed singleton
 * {@link RetentionPolicy} (B2, M9 #7 / M14 #6), editable by ADMIN — no restart required. When the
 * policy is inactive the sweep is skipped.
 *
 * <p>Deliberately NON-destructive: financial documents must not be auto-deleted without an explicit,
 * audited legal-hold/disposition decision. This job surfaces what is past retention so an
 * administrator can act; the actual purge/cold-archive transition is a separate, deliberate step.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentRetentionJob {

    private final InvoiceDocumentRepository invoiceDocumentRepository;
    private final AuditService auditService;
    private final RetentionPolicyService retentionPolicyService;

    // Daily at 02:30. cron is overridable via app.retention.cron.
    @Scheduled(cron = "${app.retention.cron:0 30 2 * * *}")
    @Transactional
    public void flagDocumentsPastRetention() {
        RetentionPolicy policy = retentionPolicyService.getEntity();
        if (!policy.isActive()) {
            log.debug("Retention sweep skipped: policy is inactive.");
            return;
        }
        int retentionYears = policy.getRetentionYears();
        Instant now = Instant.now();
        Instant cutoff = now.minus(retentionYears * 365L, ChronoUnit.DAYS);
        List<InvoiceDocument> expired = invoiceDocumentRepository.findByUploadedAtBeforeAndRetentionDisposition(cutoff, RetentionDisposition.PENDING);

        if (expired.isEmpty()) {
            log.debug("Retention sweep: no documents past {}-year retention.", retentionYears);
        } else {
            log.info("Retention sweep: {} document(s) past the {}-year retention period (cutoff {}).",
                    expired.size(), retentionYears, cutoff);
            for (InvoiceDocument doc : expired) {
                // Audited, non-destructive flag — an admin decides on disposition.
                auditService.logAction(null, "INVOICE", doc.getId().toString(), "RETENTION_FLAG",
                        null, "{\"uploadedAt\":\"" + doc.getUploadedAt() + "\",\"retentionYears\":" + retentionYears + "}",
                        null, null);
            }
        }
        retentionPolicyService.recordSweep(now, expired.size());
    }
}
