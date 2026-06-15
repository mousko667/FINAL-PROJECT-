package com.oct.invoicesystem.domain.supplier.service;

import com.oct.invoicesystem.domain.supplier.dto.SupplierCommunicationDTO;
import com.oct.invoicesystem.domain.supplier.dto.SupplierContractDTO;
import com.oct.invoicesystem.domain.supplier.model.SupplierCommunication;
import com.oct.invoicesystem.domain.supplier.model.SupplierContract;
import com.oct.invoicesystem.domain.supplier.repository.SupplierCommunicationRepository;
import com.oct.invoicesystem.domain.supplier.repository.SupplierContractRepository;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Supplier relationship management (M8): contracts + communication log. */
@Service
@RequiredArgsConstructor
public class SupplierRelationshipService {

    private static final Set<String> CONTRACT_STATUSES = Set.of("ACTIVE", "EXPIRED", "TERMINATED");
    private static final Set<String> CHANNELS = Set.of("EMAIL", "PHONE", "MEETING", "NOTE");

    private final SupplierContractRepository contractRepository;
    private final SupplierCommunicationRepository communicationRepository;
    private final SupplierRepository supplierRepository;

    private void assertSupplierExists(UUID supplierId) {
        if (supplierRepository.findByIdAndDeletedAtIsNull(supplierId).isEmpty()) {
            throw new ResourceNotFoundException("Supplier not found: " + supplierId);
        }
    }

    // ── Contracts ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<SupplierContractDTO.Response> listContracts(UUID supplierId) {
        return contractRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId).stream()
                .map(this::toContractDto).toList();
    }

    @Transactional
    public SupplierContractDTO.Response addContract(UUID supplierId, SupplierContractDTO.Request req, UUID actorId) {
        assertSupplierExists(supplierId);
        String status = req.status() == null || req.status().isBlank() ? "ACTIVE" : req.status().toUpperCase();
        if (!CONTRACT_STATUSES.contains(status)) {
            throw new ValidationException("Invalid contract status (ACTIVE|EXPIRED|TERMINATED): " + req.status());
        }
        if (req.startDate() != null && req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new ValidationException("End date must be on/after start date");
        }
        SupplierContract c = SupplierContract.builder()
                .supplierId(supplierId).reference(req.reference()).title(req.title())
                .startDate(req.startDate()).endDate(req.endDate()).status(status)
                .notes(req.notes()).createdBy(actorId).build();
        return toContractDto(contractRepository.save(c));
    }

    @Transactional
    public void deleteContract(UUID supplierId, UUID contractId) {
        SupplierContract c = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found: " + contractId));
        if (!c.getSupplierId().equals(supplierId)) {
            throw new ValidationException("Contract does not belong to this supplier");
        }
        contractRepository.delete(c);
    }

    // ── Communications ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<SupplierCommunicationDTO.Response> listCommunications(UUID supplierId) {
        return communicationRepository.findBySupplierIdOrderByLoggedAtDesc(supplierId).stream()
                .map(this::toCommDto).toList();
    }

    @Transactional
    public SupplierCommunicationDTO.Response addCommunication(UUID supplierId, SupplierCommunicationDTO.Request req, UUID actorId) {
        assertSupplierExists(supplierId);
        String channel = req.channel() == null || req.channel().isBlank() ? "NOTE" : req.channel().toUpperCase();
        if (!CHANNELS.contains(channel)) {
            throw new ValidationException("Invalid channel (EMAIL|PHONE|MEETING|NOTE): " + req.channel());
        }
        SupplierCommunication c = SupplierCommunication.builder()
                .supplierId(supplierId).channel(channel).subject(req.subject()).body(req.body())
                .loggedBy(actorId).build();
        return toCommDto(communicationRepository.save(c));
    }

    private SupplierContractDTO.Response toContractDto(SupplierContract c) {
        return new SupplierContractDTO.Response(c.getId(), c.getSupplierId(), c.getReference(), c.getTitle(),
                c.getStartDate(), c.getEndDate(), c.getStatus(), c.getNotes(), c.getCreatedAt());
    }

    private SupplierCommunicationDTO.Response toCommDto(SupplierCommunication c) {
        return new SupplierCommunicationDTO.Response(c.getId(), c.getSupplierId(), c.getChannel(),
                c.getSubject(), c.getBody(), c.getLoggedAt());
    }
}
