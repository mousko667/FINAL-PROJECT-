package com.oct.invoicesystem.domain.supplier.service;

import com.oct.invoicesystem.domain.supplier.mapper.SupplierMapper;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocument;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocumentType;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.supplier.repository.SupplierDocumentRepository;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock private SupplierRepository supplierRepository;
    @Mock private SupplierMapper supplierMapper;
    @Mock private SupplierDocumentRepository supplierDocumentRepository;
    @InjectMocks private SupplierServiceImpl service;

    private Supplier supplierWithBasics(UUID id) {
        return Supplier.builder()
                .id(id)
                .companyName("Acme")
                .taxId("TAX-001")
                .contactEmail("acme@example.com")
                .bankDetails("BANK-123")
                .status(SupplierStatus.PENDING_VERIFICATION)
                .build();
    }

    @Test
    void activateSupplier_marksSupplierActiveAndRecordsOnboardingMetadata() {
        UUID supplierId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Supplier supplier = supplierWithBasics(supplierId);
        User activatedBy = User.builder().id(adminId).username("admin").build();

        when(supplierRepository.findByIdAndDeletedAtIsNull(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierDocumentRepository.findBySupplierId(supplierId)).thenReturn(List.of(
                SupplierDocument.builder().documentType(SupplierDocumentType.TAX_CERTIFICATE).build(),
                SupplierDocument.builder().documentType(SupplierDocumentType.CONTRACT).build()
        ));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.activateSupplier(supplierId, activatedBy);

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository).save(captor.capture());
        Supplier saved = captor.getValue();
        assertEquals(SupplierStatus.ACTIVE, saved.getStatus());
        assertEquals(adminId, saved.getOnboardedBy().getId());
        assertNotNull(saved.getOnboardedAt());
    }

    @Test
    void activateSupplier_missingDocuments_throwsValidationException() {
        UUID supplierId = UUID.randomUUID();
        Supplier supplier = supplierWithBasics(supplierId);

        when(supplierRepository.findByIdAndDeletedAtIsNull(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierDocumentRepository.findBySupplierId(supplierId)).thenReturn(List.of(
                SupplierDocument.builder().documentType(SupplierDocumentType.TAX_CERTIFICATE).build()
        ));

        assertThrows(ValidationException.class, () -> service.activateSupplier(
                supplierId, User.builder().id(UUID.randomUUID()).username("admin").build()));
    }

    @Test
    void activateSupplier_notFound_throwsResourceNotFoundException() {
        UUID supplierId = UUID.randomUUID();
        when(supplierRepository.findByIdAndDeletedAtIsNull(supplierId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.activateSupplier(
                supplierId, User.builder().id(UUID.randomUUID()).username("admin").build()));
    }
}
