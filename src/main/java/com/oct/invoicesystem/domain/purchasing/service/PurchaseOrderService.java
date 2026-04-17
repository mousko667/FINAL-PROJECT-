package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderItem;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderStatus;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing Purchase Orders (PO).
 * Handles creation, updates, linking to suppliers, and retrieval of POs with their items.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;

    /**
     * Create a new purchase order with items.
     *
     * @param poNumber the unique PO number
     * @param supplierId the supplier UUID
     * @param items the list of purchase order items
     * @param createdBy the user creating the PO
     * @return the created PurchaseOrder
     * @throws ResourceNotFoundException if supplier not found
     * @throws BusinessRuleViolationException if PO number already exists
     */
    public PurchaseOrder createPurchaseOrder(String poNumber, UUID supplierId, List<PurchaseOrderItem> items, User createdBy) {
        // Verify unique PO number
        if (purchaseOrderRepository.findByPoNumber(poNumber).isPresent()) {
            throw new ValidationException("PO number already exists: " + poNumber);
        }

        // Fetch supplier
        Supplier supplier = supplierRepository.findById(supplierId)
            .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));

        // Calculate total amount
        BigDecimal totalAmount = items.stream()
            .map(PurchaseOrderItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build and save PO
        PurchaseOrder po = PurchaseOrder.builder()
            .poNumber(poNumber)
            .supplier(supplier)
            .totalAmount(totalAmount)
            .status(PurchaseOrderStatus.OPEN)
            .createdBy(createdBy)
            .items(items)
            .build();

        items.forEach(item -> item.setPurchaseOrder(po));

        log.info("Creating purchase order {} for supplier {}", poNumber, supplierId);
        return purchaseOrderRepository.save(po);
    }

    /**
     * Update an existing purchase order.
     *
     * @param id the purchase order UUID
     * @param poNumber the updated PO number (may be null to keep existing)
     * @param status the updated status (may be null to keep existing)
     * @return the updated PurchaseOrder
     * @throws ResourceNotFoundException if PO not found
     * @throws BusinessRuleViolationException if PO is deleted or already exists
     */
    public PurchaseOrder updatePurchaseOrder(UUID id, String poNumber, PurchaseOrderStatus status) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));

        if (po.isDeleted()) {
            throw new ValidationException("Cannot update a deleted purchase order");
        }

        // Check if PO can be transitioned to the new status
        if (status != null && status.equals(PurchaseOrderStatus.CLOSED) && !po.getStatus().equals(PurchaseOrderStatus.OPEN)) {
            throw new ValidationException("Only OPEN purchase orders can be closed");
        }

        if (poNumber != null && !poNumber.equals(po.getPoNumber())) {
            if (purchaseOrderRepository.findByPoNumber(poNumber).isPresent()) {
                throw new ValidationException("PO number already exists: " + poNumber);
            }
            po.setPoNumber(poNumber);
        }

        if (status != null) {
            po.setStatus(status);
        }

        log.info("Updating purchase order {}", id);
        return purchaseOrderRepository.save(po);
    }

    /**
     * List all purchase orders for a supplier.
     *
     * @param supplierId the supplier UUID
     * @return list of POs for the supplier
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrder> listBySupplier(UUID supplierId) {
        log.info("Listing purchase orders for supplier {}", supplierId);
        return purchaseOrderRepository.findBySupplierId(supplierId);
    }

    /**
     * Get a purchase order by ID with all items loaded.
     *
     * @param id the purchase order UUID
     * @return the PurchaseOrder with items
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public PurchaseOrder getPurchaseOrderWithItems(UUID id) {
        log.info("Fetching purchase order {} with items", id);
        return purchaseOrderRepository.findByIdActive(id)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    /**
     * Get a purchase order by PO number.
     *
     * @param poNumber the PO number
     * @return the matching PurchaseOrder
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public PurchaseOrder getPurchaseOrderByNumber(String poNumber) {
        return purchaseOrderRepository.findByPoNumber(poNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found with number: " + poNumber));
    }

    /**
     * Soft-delete a purchase order.
     *
     * @param id the purchase order UUID
     * @throws ResourceNotFoundException if not found
     * @throws BusinessRuleViolationException if PO is already deleted
     */
    public void deletePurchaseOrder(UUID id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));

        if (po.isDeleted()) {
            throw new ValidationException("Purchase order is already deleted");
        }

        po.setDeletedAt(java.time.Instant.now());
        log.info("Soft-deleted purchase order {}", id);
        purchaseOrderRepository.save(po);
    }
}
