package com.oct.invoicesystem.domain.supplier.controller;

import com.oct.invoicesystem.domain.supplier.dto.SupplierCommunicationDTO;
import com.oct.invoicesystem.domain.supplier.dto.SupplierContractDTO;
import com.oct.invoicesystem.domain.supplier.service.SupplierRelationshipService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Supplier contracts + communication log (M8). Read = ADMIN/AA/DAF; write = ADMIN/AA. */
@RestController
@RequestMapping("/api/v1/suppliers/{supplierId}")
@RequiredArgsConstructor
@Tag(name = "Supplier Relationship", description = "Contracts and communication log per supplier")
public class SupplierRelationshipController {

    private final SupplierRelationshipService service;
    private final SecurityHelper securityHelper;

    // ── Contracts ──
    @GetMapping("/contracts")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ResponseEntity<ApiResponse<List<SupplierContractDTO.Response>>> listContracts(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.success(service.listContracts(supplierId)));
    }

    @PostMapping("/contracts")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ResponseEntity<ApiResponse<SupplierContractDTO.Response>> addContract(
            @PathVariable UUID supplierId, @Valid @RequestBody SupplierContractDTO.Request req,
            Authentication authentication) {
        UUID actor = securityHelper.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.addContract(supplierId, req, actor), "Contract added"));
    }

    @DeleteMapping("/contracts/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ResponseEntity<ApiResponse<Void>> deleteContract(
            @PathVariable UUID supplierId, @PathVariable UUID contractId) {
        service.deleteContract(supplierId, contractId);
        return ResponseEntity.ok(ApiResponse.success(null, "Contract deleted"));
    }

    // ── Communications ──
    @GetMapping("/communications")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ResponseEntity<ApiResponse<List<SupplierCommunicationDTO.Response>>> listComms(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.success(service.listCommunications(supplierId)));
    }

    @PostMapping("/communications")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ResponseEntity<ApiResponse<SupplierCommunicationDTO.Response>> addComm(
            @PathVariable UUID supplierId, @Valid @RequestBody SupplierCommunicationDTO.Request req,
            Authentication authentication) {
        UUID actor = securityHelper.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.addCommunication(supplierId, req, actor), "Communication logged"));
    }
}
