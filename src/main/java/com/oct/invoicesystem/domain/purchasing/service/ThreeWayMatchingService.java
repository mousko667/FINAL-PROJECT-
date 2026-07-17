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
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderItemRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingLineResolutionRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingLineResolution;
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
    private final MatchingComparator matchingComparator;
    private final ThreeWayMatchingLineResolutionRepository resolutionRepository;
    private final PurchaseOrderItemRepository poItemRepository;

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
                if (matchingComparator.isWithinTolerance(invoiceQty, poQty, invoicePrice, poPrice, config)) {
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
                    if (!matchingComparator.isWithinTolerance(receivedQty, invoiceQty, BigDecimal.ONE, BigDecimal.ONE, config)) {
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
            throw new ValidationException("error.matching.override_reason_too_short");
        }

        ThreeWayMatchingResult existing = matchingResultRepository.findByInvoiceId(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("No matching result found for invoice"));

        if (!existing.getStatus().equals(MatchingStatus.MISMATCH)) {
            throw new ValidationException("error.matching.only_mismatch_overridable");
        }

        ThreeWayMatchingResult override = ThreeWayMatchingResult.builder()
            .invoice(existing.getInvoice())
            .purchaseOrder(existing.getPurchaseOrder())
            .goodsReceiptNote(existing.getGoodsReceiptNote())
            .status(MatchingStatus.OVERRIDDEN)
            .discrepancyNotes(existing.getDiscrepancyNotes())
            .overriddenBy(overriddenBy)
            .overrideReason(reason)
            .build();

        log.info("Recording override for invoice {} by user {}", invoiceId, overriddenBy.getId());
        return matchingResultRepository.save(override);
    }

    /**
     * Records a line-level resolution for a mismatched line and updates the invoice matching state if all are resolved.
     */
    public ThreeWayMatchingResult resolveLine(java.util.UUID invoiceId, java.util.UUID poLineId, String reason, com.oct.invoicesystem.domain.user.model.User resolvedBy) {
        if (reason == null || reason.trim().length() < 5) {
            throw new ValidationException("Resolution reason must be at least 5 characters");
        }

        ThreeWayMatchingResult existing = matchingResultRepository.findByInvoiceId(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("No matching result found for invoice"));

        PurchaseOrderItem poItem = poItemRepository.findById(poLineId)
            .orElseThrow(() -> new ResourceNotFoundException("PO Line not found"));

        // Save or update resolution
        ThreeWayMatchingLineResolution resolution = resolutionRepository.findByInvoiceIdAndPoLineId(invoiceId, poLineId)
            .orElse(ThreeWayMatchingLineResolution.builder()
                .invoice(existing.getInvoice())
                .poLine(poItem)
                .build());
        
        resolution.setStatus("RESOLVED");
        resolution.setReason(reason.trim());
        resolution.setResolvedBy(resolvedBy);
        resolutionRepository.save(resolution);

        // Check if all lines are now resolved
        // We have to re-evaluate the full matching. We can call performMatching and check if the only MISMATCH lines have resolutions.
        // Wait, performMatching returns MISMATCH if any line is outside tolerance.
        // So we need to re-run the matching logic but treat resolved lines as MATCHED.
        MatchingConfig config = matchingConfigRepository.findByIsActiveTrue()
            .orElseThrow(() -> new ValidationException("No active matching configuration found"));

        MatchingStatus newStatus = performMatchingWithResolutions(existing.getInvoice(), existing.getPurchaseOrder(), existing.getGoodsReceiptNote(), config);
        
        if (newStatus != existing.getStatus()) {
            ThreeWayMatchingResult update = ThreeWayMatchingResult.builder()
                .invoice(existing.getInvoice())
                .purchaseOrder(existing.getPurchaseOrder())
                .goodsReceiptNote(existing.getGoodsReceiptNote())
                .status(newStatus)
                .discrepancyNotes(existing.getDiscrepancyNotes())
                .overriddenBy(resolvedBy) // Record that it was unblocked by this user via line resolution
                .overrideReason("All mismatched lines resolved manually")
                .build();
            return matchingResultRepository.save(update);
        }

        return existing;
    }

    private MatchingStatus performMatchingWithResolutions(Invoice invoice, PurchaseOrder purchaseOrder, 
                                           GoodsReceiptNote goodsReceiptNote, MatchingConfig config) {
        
        List<ThreeWayMatchingLineResolution> resolutions = resolutionRepository.findByInvoiceId(invoice.getId());

        List<InvoiceItem> invoiceItems = invoice.getItems();
        List<PurchaseOrderItem> poItems = purchaseOrder.getItems();

        if (invoiceItems.isEmpty() || poItems.isEmpty()) {
            throw new ValidationException("Invoice or PO has no line items");
        }
        if (goodsReceiptNote != null && goodsReceiptNote.getItems().isEmpty()) {
            return MatchingStatus.MISMATCH;
        }

        boolean allLinesPerfectMatch = true;
        boolean hasAnyLineWithinTolerance = false;
        boolean hasAnyLineOutsideTolerance = false;

        for (InvoiceItem invItem : invoiceItems) {
            BigDecimal invoiceQty = invItem.getQuantity();
            BigDecimal invoicePrice = invItem.getUnitPrice();

            PurchaseOrderItem matchingPoItem = poItems.stream()
                .filter(poi -> poi.getItemDescription().equalsIgnoreCase(invItem.getDescription()))
                .findFirst()
                .orElse(null);

            if (matchingPoItem == null) {
                hasAnyLineOutsideTolerance = true;
                allLinesPerfectMatch = false;
                continue;
            }
            
            boolean isResolved = resolutions.stream().anyMatch(r -> r.getPoLine().getId().equals(matchingPoItem.getId()));

            BigDecimal poQty = matchingPoItem.getQuantity();
            BigDecimal poPrice = matchingPoItem.getUnitPrice();

            if (invoiceQty.compareTo(poQty) != 0 || invoicePrice.compareTo(poPrice) != 0) {
                allLinesPerfectMatch = false;
                if (matchingComparator.isWithinTolerance(invoiceQty, poQty, invoicePrice, poPrice, config)) {
                    hasAnyLineWithinTolerance = true;
                } else if (!isResolved) {
                    hasAnyLineOutsideTolerance = true;
                }
            }

            if (goodsReceiptNote != null) {
                BigDecimal receivedQty = goodsReceiptNote.getItems().stream()
                    .filter(gri -> gri.getPurchaseOrderItem().getId().equals(matchingPoItem.getId()))
                    .map(gri -> gri.getReceivedQuantity())
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

                if (receivedQty.compareTo(invoiceQty) != 0) {
                    if (!matchingComparator.isWithinTolerance(receivedQty, invoiceQty, BigDecimal.ONE, BigDecimal.ONE, config)) {
                        if (!isResolved) {
                            hasAnyLineOutsideTolerance = true;
                        }
                    }
                }
            }
        }

        if (!hasAnyLineOutsideTolerance) {
            // Unblocked! Because some lines were resolved manually, the status should be OVERRIDDEN
            // so the system knows it didn't match cleanly.
            if (resolutions.isEmpty() && allLinesPerfectMatch) {
                return MatchingStatus.MATCHED;
            } else if (resolutions.isEmpty() && hasAnyLineWithinTolerance) {
                return MatchingStatus.PARTIAL;
            }
            return MatchingStatus.OVERRIDDEN;
        } else {
            return MatchingStatus.MISMATCH;
        }
    }
}
