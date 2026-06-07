package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import com.oct.invoicesystem.shared.util.ReferenceNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReferenceNumberGenerator referenceNumberGenerator;

    @InjectMocks
    private InvoiceService invoiceService;

    private UUID actorId;
    private User assistant;
    private Department department;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        assistant = new User();
        assistant.setId(actorId);
        assistant.setUserRoles(null);
        assistant.setUsername("assistant");
        assistant.setPassword("x");
        assistant.setActive(true);

        department = new Department();
        department.setId(UUID.randomUUID());

        invoice = Invoice.builder()
                .department(department)
                .supplierName("ACME")
                .supplierEmail("supplier@acme.com")
                .amount(new BigDecimal("1000.00"))
                .currency("XAF")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .build();
    }

    @Test
    void createInvoice_Success() {
        assistant.setUserRoles(java.util.Set.of());
        when(userRepository.findById(actorId)).thenReturn(Optional.of(assistant));
        when(referenceNumberGenerator.nextReferenceNumber()).thenReturn("FAC-2026-00001");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        // inject authority through UserDetails contract
        assistant.setUserRoles(java.util.Set.of(
                com.oct.invoicesystem.domain.user.model.UserRole.builder()
                        .role(com.oct.invoicesystem.domain.user.model.Role.builder().name("ROLE_ASSISTANT_COMPTABLE").build())
                        .user(assistant)
                        .build()
        ));

        Invoice created = invoiceService.createInvoice(invoice, actorId);

        assertNotNull(created);
        assertEquals("FAC-2026-00001", created.getReferenceNumber());
        assertEquals(InvoiceStatus.BROUILLON, created.getStatus());
    }

    @Test
    void updateInvoice_WhenNotDraftOrRejected_ThrowsWorkflowException() {
        invoice.setId(UUID.randomUUID());
        invoice.setStatus(InvoiceStatus.VALIDE);
        assistant.setUserRoles(java.util.Set.of(
                com.oct.invoicesystem.domain.user.model.UserRole.builder()
                        .role(com.oct.invoicesystem.domain.user.model.Role.builder().name("ROLE_ASSISTANT_COMPTABLE").build())
                        .user(assistant)
                        .build()
        ));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(assistant));
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThrows(WorkflowException.class, () -> invoiceService.updateInvoice(invoice.getId(), invoice, actorId));
    }

    @Test
    void softDeleteInvoice_SetsDeletedAt() {
        invoice.setId(UUID.randomUUID());
        invoice.setStatus(InvoiceStatus.BROUILLON);
        assistant.setUserRoles(java.util.Set.of(
                com.oct.invoicesystem.domain.user.model.UserRole.builder()
                        .role(com.oct.invoicesystem.domain.user.model.Role.builder().name("ROLE_ASSISTANT_COMPTABLE").build())
                        .user(assistant)
                        .build()
        ));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(assistant));
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        invoiceService.softDeleteInvoice(invoice.getId(), actorId);

        assertNotNull(invoice.getDeletedAt());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void createInvoice_WhenActorNotAssistant_ThrowsValidationException() {
        User notAllowed = new User();
        notAllowed.setId(actorId);
        notAllowed.setUsername("admin");
        notAllowed.setPassword("x");
        notAllowed.setActive(true);
        notAllowed.setUserRoles(java.util.Set.of(
                com.oct.invoicesystem.domain.user.model.UserRole.builder()
                        .role(com.oct.invoicesystem.domain.user.model.Role.builder().name("ROLE_ADMIN").build())
                        .user(notAllowed)
                        .build()
        ));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(notAllowed));

        assertThrows(ValidationException.class, () -> invoiceService.createInvoice(invoice, actorId));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("supplierBankDetails: le champ doit être annoté @Convert pour le chiffrement AES-256")
    void supplierBankDetails_hasEncryptionConverter() throws Exception {
        java.lang.reflect.Field field = Invoice.class.getDeclaredField("supplierBankDetails");
        jakarta.persistence.Convert convertAnnotation = field.getAnnotation(jakarta.persistence.Convert.class);
        assertThat(convertAnnotation).isNotNull();
        assertThat(convertAnnotation.converter())
                .isEqualTo(com.oct.invoicesystem.shared.util.EncryptionAttributeConverter.class);
    }
}
