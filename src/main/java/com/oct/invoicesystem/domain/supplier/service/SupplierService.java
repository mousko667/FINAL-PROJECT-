package com.oct.invoicesystem.domain.supplier.service;

import com.oct.invoicesystem.domain.supplier.dto.SupplierCreateRequest;
import com.oct.invoicesystem.domain.supplier.dto.SupplierResponse;
import com.oct.invoicesystem.domain.supplier.dto.SupplierUpdateRequest;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocument;
import com.oct.invoicesystem.domain.supplier.model.SupplierCategory;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocumentType;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SupplierService {
    SupplierResponse createSupplier(SupplierCreateRequest request);
    SupplierResponse updateSupplier(UUID id, SupplierUpdateRequest request);
    SupplierResponse getSupplier(UUID id);
    Page<SupplierResponse> searchSuppliers(String name, String taxId, SupplierStatus status,
                                           SupplierCategory category, Pageable pageable);
    void activateSupplier(UUID id);
    void suspendSupplier(UUID id);
    void softDeleteSupplier(UUID id);
    Supplier findEntityById(UUID id);
    Map<String, Object> getPerformanceMetrics(UUID id);
    List<SupplierDocument> listDocuments(UUID supplierId);
    SupplierDocument uploadDocument(UUID supplierId, SupplierDocumentType documentType,
                                    String originalFilename, String objectKey,
                                    Long fileSizeBytes, String checksumSha256, User uploadedBy);
}
