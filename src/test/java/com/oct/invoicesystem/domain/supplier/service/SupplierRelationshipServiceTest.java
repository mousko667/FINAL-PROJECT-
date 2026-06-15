package com.oct.invoicesystem.domain.supplier.service;

import com.oct.invoicesystem.domain.supplier.dto.SupplierCommunicationDTO;
import com.oct.invoicesystem.domain.supplier.dto.SupplierContractDTO;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierCommunication;
import com.oct.invoicesystem.domain.supplier.model.SupplierContract;
import com.oct.invoicesystem.domain.supplier.repository.SupplierCommunicationRepository;
import com.oct.invoicesystem.domain.supplier.repository.SupplierContractRepository;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierRelationshipServiceTest {

    @Mock private SupplierContractRepository contractRepository;
    @Mock private SupplierCommunicationRepository communicationRepository;
    @Mock private SupplierRepository supplierRepository;
    @InjectMocks private SupplierRelationshipService service;

    private final UUID supplierId = UUID.randomUUID();

    private void supplierExists() {
        when(supplierRepository.findByIdAndDeletedAtIsNull(supplierId))
                .thenReturn(Optional.of(Supplier.builder().id(supplierId).build()));
    }

    @Test
    void addContract_persistsWithDefaultStatus() {
        supplierExists();
        when(contractRepository.save(any(SupplierContract.class))).thenAnswer(i -> i.getArgument(0));
        var dto = service.addContract(supplierId,
                new SupplierContractDTO.Request("C-1", "Maintenance", null, null, null, null), null);
        assertEquals("ACTIVE", dto.status());
        assertEquals("C-1", dto.reference());
    }

    @Test
    void addContract_rejectsBadStatus() {
        supplierExists();
        assertThrows(ValidationException.class, () -> service.addContract(supplierId,
                new SupplierContractDTO.Request("C", "T", null, null, "WRONG", null), null));
    }

    @Test
    void addCommunication_defaultsChannelToNote() {
        supplierExists();
        when(communicationRepository.save(any(SupplierCommunication.class))).thenAnswer(i -> i.getArgument(0));
        var dto = service.addCommunication(supplierId,
                new SupplierCommunicationDTO.Request(null, "Called supplier", "Re: invoice 12"), null);
        assertEquals("NOTE", dto.channel());
    }

    @Test
    void addCommunication_rejectsBadChannel() {
        supplierExists();
        assertThrows(ValidationException.class, () -> service.addCommunication(supplierId,
                new SupplierCommunicationDTO.Request("FAX", "x", null), null));
    }
}
