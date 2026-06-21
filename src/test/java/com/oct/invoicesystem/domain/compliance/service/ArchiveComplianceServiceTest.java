package com.oct.invoicesystem.domain.compliance.service;

import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;
import com.oct.invoicesystem.domain.retention.model.RetentionComplianceStatus;
import com.oct.invoicesystem.domain.retention.service.RetentionPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveComplianceServiceTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock InvoiceDocumentRepository documentRepository;
    @Mock RetentionPolicyService retentionPolicyService;
    @InjectMocks ArchiveComplianceService service;

    private RetentionComplianceDTO sampleRetention() {
        return new RetentionComplianceDTO(
                RetentionComplianceStatus.CONFORME, 10, true, Instant.now(), 0, false, Instant.now());
    }

    @Test
    void generateReport_computesCoverageRate() {
        when(invoiceRepository.countByStatus(InvoiceStatus.ARCHIVE)).thenReturn(8L);
        when(invoiceRepository.countArchivedWithDocument()).thenReturn(6L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.coverage().archivedInvoices()).isEqualTo(8L);
        assertThat(report.coverage().archivedWithDocument()).isEqualTo(6L);
        assertThat(report.coverage().archivedWithoutDocument()).isEqualTo(2L);
        assertThat(report.coverage().coverageRate()).isCloseTo(0.75, within(0.0001));
    }

    @Test
    void generateReport_coverageRateZeroWhenNoArchives() {
        when(invoiceRepository.countByStatus(InvoiceStatus.ARCHIVE)).thenReturn(0L);
        when(invoiceRepository.countArchivedWithDocument()).thenReturn(0L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.coverage().coverageRate()).isEqualTo(0.0);
        assertThat(report.coverage().archivedWithoutDocument()).isEqualTo(0L);
    }

    @Test
    void generateReport_computesIntegrityRate() {
        when(documentRepository.count()).thenReturn(10L);
        when(documentRepository.countWithChecksum()).thenReturn(10L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.integrity().totalDocuments()).isEqualTo(10L);
        assertThat(report.integrity().withChecksum()).isEqualTo(10L);
        assertThat(report.integrity().missingChecksum()).isEqualTo(0L);
        assertThat(report.integrity().integrityRate()).isEqualTo(1.0);
    }

    @Test
    void generateReport_integrityRateOneWhenNoDocuments() {
        when(documentRepository.count()).thenReturn(0L);
        when(documentRepository.countWithChecksum()).thenReturn(0L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.integrity().integrityRate()).isEqualTo(1.0);
    }

    @Test
    void generateReport_computesLifecycleCounts() {
        when(documentRepository.countByRetentionDisposition(RetentionDisposition.PENDING)).thenReturn(3L);
        when(documentRepository.countByRetentionDisposition(RetentionDisposition.RETAINED)).thenReturn(2L);
        when(documentRepository.countByRetentionDisposition(RetentionDisposition.PURGED)).thenReturn(1L);
        when(documentRepository.countBySupersededByDocumentIdIsNotNull()).thenReturn(4L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.lifecycle().pending()).isEqualTo(3L);
        assertThat(report.lifecycle().retained()).isEqualTo(2L);
        assertThat(report.lifecycle().purged()).isEqualTo(1L);
        assertThat(report.lifecycle().versionedDocuments()).isEqualTo(4L);
    }

    @Test
    void generateReport_embedsRetentionAndStampsTime() {
        RetentionComplianceDTO retention = sampleRetention();
        when(retentionPolicyService.evaluateCompliance()).thenReturn(retention);

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.retention()).isSameAs(retention);
        assertThat(report.generatedAt()).isNotNull();
    }
}
