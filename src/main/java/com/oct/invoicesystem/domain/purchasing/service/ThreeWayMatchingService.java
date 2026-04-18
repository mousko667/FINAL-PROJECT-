package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptNote;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderItem;
import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import com.oct.invoicesystem.domain.purchasing.repository.MatchingConfigRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for Three-Way Matching (Invoice + PO + GRN).
 * Compares quantities and price across three documents to detect discrepancies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ThreeWayMatchingService {

    private final ThreeWayMatchingResultRepository matchingResultRepository;
    private final MatchingConfigRepository matchingConfigRepository;

    /**
     * Perform three-way matching between invoice, PO, and GRN.
     *
     * @param invoice the invoice to match
     * @param purchaseOrder the associated purchase order
     * @param goodsReceiptNote the goods receipt note (optional)
     * @return MatchingStatus result
     * @throws BusinessRuleViolationException if matching cannot be performed
     */
    public ThreeWayMatchingResult match(Invoice invoice, PurchaseOrder purchaseOrder, GoodsReceiptNote goodsReceiptNote) {

        // Get current active matching config
        MatchingConfig config = matchingConfigRepository.findByIsActiveTrue()
            .orElseThrow(() -> new ValidationException("No active matching configuration found"));

        // Validate GRN requirement
        if (config.getRequireGrn() && goodsReceiptNote == null) {
            throw new ValidationException("GRN is required for matching but not provided");
        }

        // Perform matching logic
        MatchingStatus status = performMatching(invoice, purchaseOrder, goodsReceiptNote, config);

        // Record result
        ThreeWayMatchingResult result = ThreeWayMatchingResult.builder()
            .invoice(invoice)
            .purchaseOrder(purchaseOrder)
            .goodsReceiptNote(goodsReceiptNote)
            .status(status)
            .discrepancyNotes(generateDiscrepancyNotes(invoice, purchaseOrder, goodsReceiptNote, config))
            .build();

        log.info("Matching result for invoice {} with PO {}: {}", 
            invoice.getId(), purchaseOrder.getId(), status);

        return matchingResultRepository.save(result);
    }

    /**
     * Perform the actual matching logic comparing quantities and prices.
     *
     * @param invoice the invoice
     * @param purchaseOrder the PO
     * @param goodsReceiptNote the GRN (may be null)
     * @param config the matching config with tolerance settings
     * @return the matching status
     */
    private MatchingStatus performMatching(Invoice invoice, PurchaseOrder purchaseOrder, 
                                           GoodsReceiptNote goodsReceiptNote, MatchingConfig config) {

        List<InvoiceItem> invoiceItems = invoice.getItems();
        List<PurchaseOrderItem> poItems = purchaseOrder.getItems();

        if (invoiceItems.isEmpty() || poItems.isEmpty()) {
            throw new ValidationException("Invoice or PO has no line items");
        }

        // If GRN provided, validate GRN items
        if (goodsReceiptNote != null && goodsReceiptNote.getItems().isEmpty()) {
            return MatchingStatus.MISMATCH;
        }

        // Build maps for easier lookup
        Map<String, BigDecimal> invoiceMap = invoiceItems.stream()
            .collect(Collectors.toMap(InvoiceItem::getDescription, InvoiceItem::getQuantity));

        Map<String, BigDecimal> poMap = poItems.stream()
            .collect(Collectors.toMap(PurchaseOrderItem::getItemDescription, PurchaseOrderItem::getQuantity));

        boolean allLinesPerfectMatch = true;
        boolean hasAnyLineWithinTolerance = false;
        boolean hasAnyLineOutsideTolerance = false;

        // Check each invoice line
        for (InvoiceItem invItem : invoiceItems) {
            BigDecimal invoiceQty = invItem.getQuantity();
            BigDecimal invoicePrice = invItem.getUnitPrice();

            // Find matching PO item by description
            PurchaseOrderItem matchingPoItem = poItems.stream()
                .filter(poi -> poi.getItemDescription().equalsIgnoreCase(invItem.getDescription()))
                .findFirst()
                .orElse(null);

            if (matchingPoItem == null) {
                hasAnyLineOutsideTolerance = true;
                allLinesPerfectMatch = false;
                continue;
            }

            BigDecimal poQty = matchingPoItem.getQuantity();
            BigDecimal poPrice = matchingPoItem.getUnitPrice();

            // Check if values are exactly matching
            if (invoiceQty.compareTo(poQty) != 0 || invoicePrice.compareTo(poPrice) != 0) {
                allLinesPerfectMatch = false;

                // Apply tolerance
                if (isWithinTolerance(invoiceQty, poQty, invoicePrice, poPrice, config)) {
                    hasAnyLineWithinTolerance = true;
                } else {
                    hasAnyLineOutsideTolerance = true;
                }
            }

            // If GRN present, validate received quantity
            if (goodsReceiptNote != null) {
                BigDecimal receivedQty = goodsReceiptNote.getItems().stream()
                    .filter(gri -> gri.getPurchaseOrderItem().getId().equals(matchingPoItem.getId()))
                    .map(gri -> gri.getReceivedQuantity())
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

                if (receivedQty.compareTo(invoiceQty) != 0) {
                    if (!isWithinTolerance(receivedQty, invoiceQty, BigDecimal.ONE, BigDecimal.ONE, config)) {
                        hasAnyLineOutsideTolerance = true;
                    }
                }
            }
        }

        // Determine final status
        if (!hasAnyLineOutsideTolerance) {
            if (allLinesPerfectMatch) {
                return MatchingStatus.MATCHED;
            } else if (hasAnyLineWithinTolerance) {
                return MatchingStatus.PARTIAL;
            }
            return MatchingStatus.MATCHED;
        } else {
            return MatchingStatus.MISMATCH;
        }
    }

    /**
     * Check if a variance is within configured tolerance.
     *
     * @param invoiceValue the invoice value
     * @param poValue the PO value
     * @param invoicePrice the invoice unit price
     * @param poPrice the PO unit price
     * @param config the matching config
     * @return true if within tolerance
     */
    private boolean isWithinTolerance(BigDecimal invoiceValue, BigDecimal poValue, 
                                     BigDecimal invoicePrice, BigDecimal poPrice, MatchingConfig config) {
        BigDecimal percTolerance = config.getTolerancePercentage();
        BigDecimal amtTolerance = config.getToleranceAmount();

        // Calculate variance
        BigDecimal qtyVariance = invoiceValue.subtract(poValue).abs();
        BigDecimal priceVariance = invoicePrice.subtract(poPrice).abs();

        // Check quantity tolerance
        BigDecimal qtyThreshold = poValue.multiply(percTolerance).divide(new BigDecimal("100"));
        BigDecimal qtyVarianceAmount = qtyVariance.multiply(poPrice);
        if (qtyVariance.compareTo(qtyThreshold) > 0 && qtyVarianceAmount.compareTo(amtTolerance) > 0) {
            return false;
        }

        // Check price tolerance
        BigDecimal priceThreshold = poPrice.multiply(percTolerance).divide(new BigDecimal("100"));
        BigDecimal priceVarianceAmount = priceVariance.multiply(poValue);
        if (priceVariance.compareTo(priceThreshold) > 0 && priceVarianceAmount.compareTo(amtTolerance) > 0) {
            return false;
        }

        return true;
    }

    /**
     * Generate human-readable discrepancy notes.
     *
     * @param invoice the invoice
     * @param purchaseOrder the PO
     * @param goodsReceiptNote the GRN (may be null)
     * @param config the matching config
     * @return discrepancy notes string
     */
    private String generateDiscrepancyNotes(Invoice invoice, PurchaseOrder purchaseOrder, 
                                           GoodsReceiptNote goodsReceiptNote, MatchingConfig config) {
        List<String> notes = new ArrayList<>();

        notes.add(String.format("Invoice total: %.2f %s", invoice.getAmount(), invoice.getCurrency()));
        notes.add(String.format("PO total: %.2f %s", purchaseOrder.getTotalAmount(), invoice.getCurrency()));

        if (goodsReceiptNote != null) {
            notes.add(String.format("GRN: %s (received on %s)", goodsReceiptNote.getGrnNumber(), goodsReceiptNote.getReceiptDate()));
        }

        notes.add(String.format("Applied tolerance: %s%% or %s %s",
            config.getTolerancePercentage(),
            config.getToleranceAmount(),
            invoice.getCurrency()));

        return String.join("; ", notes);
    }

    /**
     * Record an override for a mismatched invoice.
     * Creates a new result record with OVERRIDDEN status.
     *
     * @param invoiceId the invoice UUID
     * @param overriddenBy the user overriding the mismatch
     * @param reason the override reason (must be at least 10 characters)
     * @return the override result
     * @throws BusinessRuleViolationException if reason is too short
     * @throws ResourceNotFoundException if matching result not found
     */
    public ThreeWayMatchingResult recordOverride(java.util.UUID invoiceId, com.oct.invoicesystem.domain.user.model.User overriddenBy, String reason) {
        if (reason == null || reason.length() < 10) {
            throw new ValidationException("Override reason must be at least 10 characters");
        }

        ThreeWayMatchingResult existing = matchingResultRepository.findByInvoiceId(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("No matching result found for invoice"));

        if (!existing.getStatus().equals(MatchingStatus.MISMATCH)) {
            throw new ValidationException("Can only override MISMATCH results");
        }

        existing.setStatus(MatchingStatus.OVERRIDDEN);
        existing.setOverriddenBy(overriddenBy);
        existing.setOverrideReason(reason);

        log.info("Recording override for invoice {} by user {}", invoiceId, overriddenBy.getId());
        return matchingResultRepository.save(existing);
    }
}
