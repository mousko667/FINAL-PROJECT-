package com.oct.invoicesystem.domain.purchasing.controller;

import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderCreateRequest;
import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderDTO;
import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderItemCreateRequest;
import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderUpdateRequest;
import com.oct.invoicesystem.domain.purchasing.mapper.PurchaseOrderMapper;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderItem;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderStatus;
import com.oct.invoicesystem.domain.purchasing.service.PurchaseOrderService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Purchase Order Management", description = "Endpoints for managing purchase orders")
@SecurityRequirement(name = "bearerAuth")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ASSISTANT_COMPTABLE')")
    @Operation(summary = "Create purchase order", description = "Creates a new purchase order with items")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderCreateRequest request,
            Authentication authentication) {
        UUID actorId = getActorId(authentication);
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorId));

        // Convert PO items
        List<PurchaseOrderItem> items = request.items().stream()
                .map(itemReq -> {
                    BigDecimal lineTotal = itemReq.quantity().multiply(itemReq.unitPrice());
                    return PurchaseOrderItem.builder()
                            .itemDescription(itemReq.itemDescription())
                            .quantity(itemReq.quantity())
                            .unitPrice(itemReq.unitPrice())
                            .lineTotal(lineTotal)
                            .build();
                })
                .toList();

        PurchaseOrder created = purchaseOrderService.createPurchaseOrder(
                request.poNumber(),
                request.supplierId(),
                items,
                actor
        );

        PurchaseOrderDTO dto = purchaseOrderMapper.toPurchaseOrderDTO(created);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto, "purchase_order.created"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get purchase order", description = "Retrieves a purchase order with all items")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> getPurchaseOrder(@PathVariable UUID id) {
        PurchaseOrder po = purchaseOrderService.getPurchaseOrderWithItems(id);
        PurchaseOrderDTO dto = purchaseOrderMapper.toPurchaseOrderDTO(po);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ASSISTANT_COMPTABLE')")
    @Operation(summary = "List purchase orders by supplier", description = "Lists all purchase orders for a given supplier")
    public ResponseEntity<ApiResponse<List<PurchaseOrderDTO>>> listBySupplier(
            @RequestParam UUID supplierId) {
        List<PurchaseOrder> pos = purchaseOrderService.listBySupplier(supplierId);
        List<PurchaseOrderDTO> dtos = pos.stream()
                .map(purchaseOrderMapper::toPurchaseOrderDTO)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ASSISTANT_COMPTABLE')")
    @Operation(summary = "Update purchase order", description = "Updates a purchase order status and items")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> updatePurchaseOrder(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseOrderUpdateRequest request) {
        PurchaseOrderStatus status = PurchaseOrderStatus.valueOf(request.status());
        PurchaseOrder updated = purchaseOrderService.updatePurchaseOrder(id, null, status);
        PurchaseOrderDTO dto = purchaseOrderMapper.toPurchaseOrderDTO(updated);
        return ResponseEntity.ok(ApiResponse.success(dto, "purchase_order.updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ASSISTANT_COMPTABLE')")
    @Operation(summary = "Delete purchase order", description = "Soft-deletes a purchase order")
    public ResponseEntity<ApiResponse<Void>> deletePurchaseOrder(@PathVariable UUID id) {
        purchaseOrderService.deletePurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.success(null, "purchase_order.deleted"));
    }

    private UUID getActorId(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
