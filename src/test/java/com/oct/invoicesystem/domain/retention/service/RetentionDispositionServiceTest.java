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

    /** Builds an actor carrying the given role names, so the two-man rule (AUDIT-009) can be exercised. */
    private User actorWithRoles(String... roleNames) {
        User u = new User();
        u.setId(UUID.randomUUID());
        java.util.Set<com.oct.invoicesystem.domain.user.model.UserRole> roles = new java.util.HashSet<>();
        for (String name : roleNames) {
            com.oct.invoicesystem.domain.user.model.Role role =
                    com.oct.invoicesystem.domain.user.model.Role.builder().id(UUID.randomUUID()).name(name).build();
            com.oct.invoicesystem.domain.user.model.UserRole ur =
                    new com.oct.invoicesystem.domain.user.model.UserRole();
            ur.setRole(role);
            roles.add(ur);
        }
        u.setUserRoles(roles);
        return u;
    }

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
        User actor = actorWithRoles("ROLE_ADMIN");

        RetentionPendingDocumentDTO dto = service.setDisposition(id, RetentionDisposition.RETAINED, actor);

        assertThat(dto.retentionDisposition()).isEqualTo(RetentionDisposition.RETAINED);
        assertThat(d.getRetentionDispositionAt()).isNotNull();
        assertThat(d.getRetentionDispositionBy()).isEqualTo(actor);
        verify(auditService).logAction(eq(actor.getId()), eq("INVOICE_DOCUMENT"), eq(id.toString()),
                eq("RETENTION_DISPOSITION"), eq("PENDING"), eq("RETAINED"), isNull(), isNull());
    }

    @Test
    void setDisposition_adminProposesPurge_thenDafConfirms_isTheOnlyPathToPurged() {
        // AUDIT-009 / D5: destroying a financial supporting document takes two distinct roles.
        UUID id = UUID.randomUUID();
        InvoiceDocument d = doc(id, RetentionDisposition.PENDING);
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.of(d));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        // Step 1 — the ADMIN proposes.
        RetentionPendingDocumentDTO proposed =
                service.setDisposition(id, RetentionDisposition.PURGE_PROPOSED, actorWithRoles("ROLE_ADMIN"));
        assertThat(proposed.retentionDisposition()).isEqualTo(RetentionDisposition.PURGE_PROPOSED);

        // Step 2 — the DAF confirms, and only then does the document reach PURGED.
        RetentionPendingDocumentDTO confirmed =
                service.setDisposition(id, RetentionDisposition.PURGED, actorWithRoles("ROLE_DAF"));
        assertThat(confirmed.retentionDisposition()).isEqualTo(RetentionDisposition.PURGED);
    }

    @Test
    void setDisposition_adminCannotPurgeAlone() {
        // The whole point of the finding: the technical admin must never destroy on their own.
        UUID id = UUID.randomUUID();
        InvoiceDocument d = doc(id, RetentionDisposition.PURGE_PROPOSED);
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.setDisposition(id, RetentionDisposition.PURGED, actorWithRoles("ROLE_ADMIN")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("purge_requires_daf");
        verify(invoiceDocumentRepository, never()).save(any());
    }

    @Test
    void setDisposition_dafCannotPurgeWhatWasNeverProposed() {
        // The DAF is the second man, not a shortcut: no proposal, no purge.
        UUID id = UUID.randomUUID();
        InvoiceDocument d = doc(id, RetentionDisposition.PENDING);
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.setDisposition(id, RetentionDisposition.PURGED, actorWithRoles("ROLE_DAF")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("purge_not_proposed");
        verify(invoiceDocumentRepository, never()).save(any());
    }

    @Test
    void setDisposition_dafCannotProposeTheirOwnPurge() {
        // Otherwise the DAF could propose then confirm, and the two-man rule would be cosmetic.
        UUID id = UUID.randomUUID();
        InvoiceDocument d = doc(id, RetentionDisposition.PENDING);
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.setDisposition(id, RetentionDisposition.PURGE_PROPOSED, actorWithRoles("ROLE_DAF")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("proposal_requires_admin");
        verify(invoiceDocumentRepository, never()).save(any());
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
        User actor = actorWithRoles("ROLE_ADMIN");

        assertThatThrownBy(() -> service.setDisposition(id, RetentionDisposition.RETAINED, actor))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
