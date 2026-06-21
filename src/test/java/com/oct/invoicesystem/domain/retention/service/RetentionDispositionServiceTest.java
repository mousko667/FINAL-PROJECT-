package com.oct.invoicesystem.domain.retention.service;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.retention.dto.RetentionPendingDocumentDTO;
import com.oct.invoicesystem.domain.retention.model.RetentionPolicy;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionDispositionServiceTest {

    @Mock InvoiceDocumentRepository invoiceDocumentRepository;
    @Mock com.oct.invoicesystem.domain.retention.service.RetentionPolicyService retentionPolicyService;
    @Mock AuditService auditService;
    @InjectMocks RetentionDispositionService service;

    private InvoiceDocument doc(UUID id, RetentionDisposition disp) {
        Invoice inv = new Invoice();
        inv.setId(UUID.randomUUID());
        InvoiceDocument d = new InvoiceDocument();
        d.setId(id);
        d.setInvoice(inv);
        d.setOriginalFilename("old.pdf");
        d.setUploadedAt(Instant.parse("2000-01-01T00:00:00Z"));
        d.setRetentionDisposition(disp);
        return d;
    }

    @Test
    void listPendingExpired_returnsOnlyExpiredPendingMappedToDto() {
        when(retentionPolicyService.getEntity())
                .thenReturn(RetentionPolicy.builder().retentionYears(10).active(true).build());
        InvoiceDocument d = doc(UUID.randomUUID(), RetentionDisposition.PENDING);
        when(invoiceDocumentRepository.findByUploadedAtBeforeAndRetentionDisposition(any(), eq(RetentionDisposition.PENDING)))
                .thenReturn(List.of(d));

        List<RetentionPendingDocumentDTO> result = service.listPendingExpired();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(d.getId());
        assertThat(result.get(0).originalFilename()).isEqualTo("old.pdf");
        assertThat(result.get(0).retentionDisposition()).isEqualTo(RetentionDisposition.PENDING);
    }

    @Test
    void setDisposition_retained_setsFieldsAndAudits() {
        UUID id = UUID.randomUUID();
        InvoiceDocument d = doc(id, RetentionDisposition.PENDING);
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.of(d));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        User actor = new User();
        actor.setId(UUID.randomUUID());

        RetentionPendingDocumentDTO dto = service.setDisposition(id, RetentionDisposition.RETAINED, actor);

        assertThat(dto.retentionDisposition()).isEqualTo(RetentionDisposition.RETAINED);
        assertThat(d.getRetentionDispositionAt()).isNotNull();
        assertThat(d.getRetentionDispositionBy()).isEqualTo(actor);
        verify(auditService).logAction(eq(actor.getId()), eq("INVOICE_DOCUMENT"), eq(id.toString()),
                eq("RETENTION_DISPOSITION"), eq("PENDING"), eq("RETAINED"), isNull(), isNull());
    }

    @Test
    void setDisposition_purged_setsField() {
        UUID id = UUID.randomUUID();
        InvoiceDocument d = doc(id, RetentionDisposition.PENDING);
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.of(d));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        User actor = new User();
        actor.setId(UUID.randomUUID());

        RetentionPendingDocumentDTO dto = service.setDisposition(id, RetentionDisposition.PURGED, actor);

        assertThat(dto.retentionDisposition()).isEqualTo(RetentionDisposition.PURGED);
    }

    @Test
    void setDisposition_toPending_isRejected() {
        UUID id = UUID.randomUUID();
        User actor = new User();
        actor.setId(UUID.randomUUID());

        assertThatThrownBy(() -> service.setDisposition(id, RetentionDisposition.PENDING, actor))
                .isInstanceOf(ValidationException.class);
        verify(invoiceDocumentRepository, never()).save(any());
    }

    @Test
    void setDisposition_unknownDoc_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.empty());
        User actor = new User();
        actor.setId(UUID.randomUUID());

        assertThatThrownBy(() -> service.setDisposition(id, RetentionDisposition.RETAINED, actor))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
