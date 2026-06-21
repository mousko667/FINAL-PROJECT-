package com.oct.invoicesystem.domain.compliance.service;

import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO;
import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO.CoverageSection;
import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO.IntegritySection;
import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO.LifecycleSection;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;
import com.oct.invoicesystem.domain.retention.service.RetentionPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Builds the read-only archive-compliance report (M14 #11): archival coverage, SHA-256 integrity,
 * retention status (reused from M10 #10), and document lifecycle (dispositions + versioning).
 * ADMIN only, no financial data; computed in real time from count queries (no table).
 */
@Service
@RequiredArgsConstructor
public class ArchiveComplianceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceDocumentRepository documentRepository;
    private final RetentionPolicyService retentionPolicyService;

    /** Computes the full archive-compliance snapshot. */
    @Transactional(readOnly = true)
    public ArchiveComplianceReportDTO generateReport() {
        return new ArchiveComplianceReportDTO(
                Instant.now(),
                buildCoverage(),
                buildIntegrity(),
                retentionPolicyService.evaluateCompliance(),
                buildLifecycle());
    }

    private CoverageSection buildCoverage() {
        long archived = invoiceRepository.countByStatus(InvoiceStatus.ARCHIVE);
        long withDoc = invoiceRepository.countArchivedWithDocument();
        long withoutDoc = archived - withDoc;
        double rate = archived == 0 ? 0.0 : (double) withDoc / archived;
        return new CoverageSection(archived, withDoc, withoutDoc, rate);
    }

    private IntegritySection buildIntegrity() {
        long total = documentRepository.count();
        long withChecksum = documentRepository.countWithChecksum();
        long missing = total - withChecksum;
        double rate = total == 0 ? 1.0 : (double) withChecksum / total;
        return new IntegritySection(total, withChecksum, missing, rate);
    }

    private LifecycleSection buildLifecycle() {
        long pending = documentRepository.countByRetentionDisposition(RetentionDisposition.PENDING);
        long retained = documentRepository.countByRetentionDisposition(RetentionDisposition.RETAINED);
        long purged = documentRepository.countByRetentionDisposition(RetentionDisposition.PURGED);
        long versioned = documentRepository.countBySupersededByDocumentIdIsNotNull();
        return new LifecycleSection(pending, retained, purged, versioned);
    }
}
