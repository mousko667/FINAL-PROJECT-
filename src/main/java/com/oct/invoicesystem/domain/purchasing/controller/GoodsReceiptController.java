package com.oct.invoicesystem.domain.purchasing.controller;

import com.oct.invoicesystem.domain.purchasing.dto.GoodsReceiptCreateRequest;
import com.oct.invoicesystem.domain.purchasing.dto.GoodsReceiptDTO;
import com.oct.invoicesystem.domain.purchasing.service.GoodsReceiptService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Create GRN", description = "Records received goods against a purchase order")
    public ResponseEntity<ApiResponse<GoodsReceiptDTO>> createGRN(
            @Valid @RequestBody GoodsReceiptCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(grnService.createGRN(request), "grn.created"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF')")
    @Operation(summary = "Get GRN", description = "Retrieves a goods receipt note by ID")
    public ResponseEntity<ApiResponse<GoodsReceiptDTO>> getGRN(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(grnService.getGRN(id)));
    }

    /**
     * AUDIT-028 : la branche « sans filtre » n'existait pas — l'endpoint renvoyait litteralement
     * {@code List.of()} quand {@code purchaseOrderId} etait absent, alors que la page « Bons de
     * Reception » l'appelle sans aucun parametre. La liste etait donc structurellement toujours
     * vide, bien que la base contienne des GRN. Implementee sur le modele de
     * {@code PurchaseOrderController.listPurchaseOrders} : filtre -> liste complete non paginee,
     * sans filtre -> page.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF')")
    @Operation(summary = "List GRNs", description = "Lists GRNs, optionally filtered by purchase order")
    public ResponseEntity<ApiResponse<PagedResponse<GoodsReceiptDTO>>> listGRNs(
            @RequestParam(required = false) UUID purchaseOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (purchaseOrderId != null) {
            List<GoodsReceiptDTO> dtos = grnService.getGRNsByPurchaseOrder(purchaseOrderId);
            return ResponseEntity.ok(ApiResponse.success(
                    PagedResponse.<GoodsReceiptDTO>builder()
                            .content(dtos)
                            .page(0)
                            .size(dtos.size())
                            .totalElements(dtos.size())
                            .totalPages(1)
                            .last(true)
                            .build()
            ));
        }

        Page<GoodsReceiptDTO> dtoPage = grnService.getAllGRNs(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(dtoPage)));
    }
}
