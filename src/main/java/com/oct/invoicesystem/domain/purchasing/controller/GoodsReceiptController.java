package com.oct.invoicesystem.domain.purchasing.controller;

import com.oct.invoicesystem.domain.purchasing.dto.GoodsReceiptCreateRequest;
import com.oct.invoicesystem.domain.purchasing.dto.GoodsReceiptDTO;
import com.oct.invoicesystem.domain.purchasing.service.GoodsReceiptService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goods-receipts")
@RequiredArgsConstructor
@Tag(name = "Goods Receipt Notes", description = "GRN management for three-way matching")
@SecurityRequirement(name = "bearerAuth")
public class GoodsReceiptController {

    private final GoodsReceiptService grnService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Create GRN", description = "Records received goods against a purchase order")
    public ResponseEntity<ApiResponse<GoodsReceiptDTO>> createGRN(
            @Valid @RequestBody GoodsReceiptCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(grnService.createGRN(request), "grn.created"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')")
    @Operation(summary = "Get GRN", description = "Retrieves a goods receipt note by ID")
    public ResponseEntity<ApiResponse<GoodsReceiptDTO>> getGRN(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(grnService.getGRN(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')")
    @Operation(summary = "List GRNs", description = "Lists GRNs, optionally filtered by purchase order")
    public ResponseEntity<ApiResponse<List<GoodsReceiptDTO>>> listGRNs(
            @RequestParam(required = false) UUID purchaseOrderId) {
        List<GoodsReceiptDTO> result = purchaseOrderId != null
                ? grnService.getGRNsByPurchaseOrder(purchaseOrderId)
                : List.of();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
