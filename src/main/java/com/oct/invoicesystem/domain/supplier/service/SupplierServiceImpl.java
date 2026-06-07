package com.oct.invoicesystem.domain.supplier.service;

import com.oct.invoicesystem.domain.supplier.dto.SupplierCreateRequest;
import com.oct.invoicesystem.domain.supplier.dto.SupplierResponse;
import com.oct.invoicesystem.domain.supplier.dto.SupplierUpdateRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;
    private final SupplierDocumentRepository supplierDocumentRepository;

    @Override
    public SupplierResponse createSupplier(SupplierCreateRequest request) {
        if (supplierRepository.existsByTaxIdAndDeletedAtIsNull(request.taxId())) {
            throw new ValidationException("error.supplier.tax_id_exists");
        }
        Supplier supplier = supplierMapper.toEntity(request);
        supplier.setStatus(SupplierStatus.PENDING_VERIFICATION);
        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Override
    public SupplierResponse updateSupplier(UUID id, SupplierUpdateRequest request) {
        Supplier supplier = findEntityById(id);
        supplierMapper.updateEntity(supplier, request);
        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(UUID id) {
        return supplierMapper.toResponse(findEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierResponse> searchSuppliers(String name, String taxId, SupplierStatus status, Pageable pageable) {
        return supplierRepository.searchSuppliers(name, taxId, status != null ? status.name() : null, pageable)
                .map(supplierMapper::toResponse);
    }

    @Override
    public void activateSupplier(UUID id) {
        Supplier supplier = findEntityById(id);
        supplier.setStatus(SupplierStatus.ACTIVE);
        supplierRepository.save(supplier);
    }

    @Override
    public void suspendSupplier(UUID id) {
        Supplier supplier = findEntityById(id);
        supplier.setStatus(SupplierStatus.SUSPENDED);
        supplierRepository.save(supplier);
    }

    @Override
    public void softDeleteSupplier(UUID id) {
        Supplier supplier = findEntityById(id);
        supplier.setDeletedAt(Instant.now());
        supplierRepository.save(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public Supplier findEntityById(UUID id) {
        return supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.supplier.not_found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPerformanceMetrics(UUID id) {
        findEntityById(id);
        // Stub for Phase 9G
        return Map.of(
            "accuracyRate", 100.0,
            "rejectionRate", 0.0,
            "averagePaymentTimeDays", 15
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierDocument> listDocuments(UUID supplierId) {
        return supplierDocumentRepository.findBySupplierId(supplierId);
    }

    @Override
    @Transactional
    public SupplierDocument uploadDocument(UUID supplierId, SupplierDocumentType documentType,
                                            String originalFilename, String objectKey,
                                            Long fileSizeBytes, String checksumSha256, User uploadedBy) {
        Supplier supplier = new Supplier();
        supplier.setId(supplierId);
        SupplierDocument document = SupplierDocument.builder()
                .supplier(supplier)
                .documentType(documentType)
                .originalFilename(originalFilename)
                .minioObjectKey(objectKey)
                .fileSizeBytes(fileSizeBytes)
                .checksumSha256(checksumSha256)
                .uploadedBy(uploadedBy)
                .build();
        return supplierDocumentRepository.save(document);
    }
}
