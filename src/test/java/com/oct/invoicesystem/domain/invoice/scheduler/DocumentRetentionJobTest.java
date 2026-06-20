package com.oct.invoicesystem.domain.invoice.scheduler;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.retention.model.RetentionPolicy;
import com.oct.invoicesystem.domain.retention.service.RetentionPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentRetentionJobTest {

    @Mock InvoiceDocumentRepository invoiceDocumentRepository;
    @Mock AuditService auditService;
    @Mock RetentionPolicyService retentionPolicyService;
    @InjectMocks DocumentRetentionJob job;

    @Test
    void inactivePolicy_skipsSweepEntirely() {
        when(retentionPolicyService.getEntity())
                .thenReturn(RetentionPolicy.builder().retentionYears(10).active(false).build());

        job.flagDocumentsPastRetention();

        verify(invoiceDocumentRepository, never()).findByUploadedAtBefore(any());
        verify(retentionPolicyService, never()).recordSweep(any(), anyInt());
    }

    @Test
    void activePolicy_flagsExpiredAndRecordsSweep() {
        when(retentionPolicyService.getEntity())
                .thenReturn(RetentionPolicy.builder().retentionYears(10).active(true).build());
        InvoiceDocument doc = new InvoiceDocument();
        doc.setId(UUID.randomUUID());
        doc.setUploadedAt(Instant.parse("2000-01-01T00:00:00Z"));
        when(invoiceDocumentRepository.findByUploadedAtBefore(any())).thenReturn(List.of(doc));

        job.flagDocumentsPastRetention();

        verify(auditService).logAction(any(), eq("INVOICE"), eq(doc.getId().toString()),
                eq("RETENTION_FLAG"), any(), any(), any(), any());
        verify(retentionPolicyService).recordSweep(any(), eq(1));
    }

    @Test
    void activePolicy_noExpiredDocs_recordsZeroSweep() {
        when(retentionPolicyService.getEntity())
                .thenReturn(RetentionPolicy.builder().retentionYears(10).active(true).build());
        when(invoiceDocumentRepository.findByUploadedAtBefore(any())).thenReturn(List.of());

        job.flagDocumentsPastRetention();

        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any(), any(), any());
        verify(retentionPolicyService).recordSweep(any(), eq(0));
    }
}
