package com.oct.invoicesystem.domain.supplier.controller;

import com.oct.invoicesystem.domain.supplier.dto.SupplierCreateRequest;
import com.oct.invoicesystem.domain.supplier.dto.SupplierResponse;
import com.oct.invoicesystem.domain.supplier.dto.SupplierUpdateRequest;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.supplier.service.SupplierService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ApiResponse<SupplierResponse> createSupplier(@Valid @RequestBody SupplierCreateRequest request) {
        return ApiResponse.success(supplierService.createSupplier(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ApiResponse<SupplierResponse> updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierUpdateRequest request) {
        return ApiResponse.success(supplierService.updateSupplier(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MANAGER', 'USER')")
    public ApiResponse<SupplierResponse> getSupplier(@PathVariable UUID id) {
        return ApiResponse.success(supplierService.getSupplier(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MANAGER', 'USER')")
    public ApiResponse<PagedResponse<SupplierResponse>> searchSuppliers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String taxId,
            @RequestParam(required = false) SupplierStatus status,
            Pageable pageable) {
        Page<SupplierResponse> page = supplierService.searchSuppliers(name, taxId, status, pageable);
        return ApiResponse.success(PagedResponse.of(page));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> activateSupplier(@PathVariable UUID id) {
        supplierService.activateSupplier(id);
        return ApiResponse.success(null, "Supplier activated successfully");
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> suspendSupplier(@PathVariable UUID id) {
        supplierService.suspendSupplier(id);
        return ApiResponse.success(null, "Supplier suspended successfully");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void softDeleteSupplier(@PathVariable UUID id) {
        supplierService.softDeleteSupplier(id);
    }

    @GetMapping("/{id}/performance")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MANAGER')")
    public ApiResponse<Map<String, Object>> getPerformanceMetrics(@PathVariable UUID id) {
        return ApiResponse.success(supplierService.getPerformanceMetrics(id));
    }
}
