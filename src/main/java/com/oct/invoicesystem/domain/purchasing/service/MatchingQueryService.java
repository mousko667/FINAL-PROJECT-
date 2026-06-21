package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.purchasing.dto.LineVerdict;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingDetailDTO;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingDetailDTO.LineComparison;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingSummaryDTO;
import com.oct.invoicesystem.domain.purchasing.model.*;
import com.oct.invoicesystem.domain.purchasing.repository.*;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Lecture seule : liste des rapprochements + recomposition ligne-à-ligne (M5 #1/#4). Aucune écriture. */
@Service
@RequiredArgsConstructor
public class MatchingQueryService {

    private final ThreeWayMatchingResultRepository matchingRepository;
    private final InvoiceRepository invoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GoodsReceiptNoteRepository goodsReceiptNoteRepository;
    private final MatchingConfigRepository matchingConfigRepository;
    private final MatchingComparator matchingComparator;

    /** Liste paginée du dernier résultat de rapprochement par facture. */
    @Transactional(readOnly = true)
    public Page<MatchingSummaryDTO> list(MatchingStatus status, String search, Pageable pageable) {
        return matchingRepository.findLatestPerInvoice(status, blankToNull(search), pageable)
                .map(this::toSummary);
    }

    /** Détail ligne-à-ligne d'un rapprochement, recalculé à la volée. */
    @Transactional(readOnly = true)
    public MatchingDetailDTO getLines(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("matching.invoice.notfound"));
        if (invoice.getPurchaseOrderId() == null) {
            throw new ResourceNotFoundException("matching.po.notfound");
        }
        PurchaseOrder po = purchaseOrderRepository.findById(invoice.getPurchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("matching.po.notfound"));
        List<GoodsReceiptNote> grns = goodsReceiptNoteRepository.findByPurchaseOrderId(invoice.getPurchaseOrderId());
        MatchingConfig config = matchingConfigRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("matching.config.notfound"));

        Map<String, BigDecimal> receivedByDesc = new HashMap<>();
        for (GoodsReceiptNote grn : grns) {
            for (GoodsReceiptItem gri : grn.getItems()) {
                String desc = gri.getPurchaseOrderItem().getItemDescription();
                receivedByDesc.merge(desc, gri.getReceivedQuantity(), BigDecimal::add);
            }
        }

        List<LineComparison> lines = new ArrayList<>();
        for (InvoiceItem inv : invoice.getItems()) {
            PurchaseOrderItem poItem = po.getItems().stream()
                    .filter(p -> p.getItemDescription().equalsIgnoreCase(inv.getDescription()))
                    .findFirst().orElse(null);
            if (poItem == null) {
                lines.add(new LineComparison(inv.getDescription(), null, null,
                        receivedByDesc.get(inv.getDescription()),
                        inv.getQuantity(), inv.getUnitPrice(), null, null, LineVerdict.MISSING_IN_PO));
                continue;
            }
            LineVerdict verdict = matchingComparator.verdictForLine(
                    inv.getQuantity(), inv.getUnitPrice(), poItem.getQuantity(), poItem.getUnitPrice(), config);
            lines.add(new LineComparison(
                    inv.getDescription(), poItem.getQuantity(), poItem.getUnitPrice(),
                    receivedByDesc.get(poItem.getItemDescription()),
                    inv.getQuantity(), inv.getUnitPrice(),
                    variancePct(inv.getQuantity(), poItem.getQuantity()),
                    variancePct(inv.getUnitPrice(), poItem.getUnitPrice()),
                    verdict));
        }

        Optional<ThreeWayMatchingResult> last = matchingRepository.findByInvoiceId(invoiceId);
        MatchingSummaryDTO summary = toSummaryFrom(invoice, po, grns, lines, last.orElse(null));
        return new MatchingDetailDTO(
                summary,
                last.map(ThreeWayMatchingResult::getDiscrepancyNotes).orElse(null),
                last.map(r -> r.getOverriddenBy() != null ? r.getOverriddenBy().getId() : null).orElse(null),
                last.map(ThreeWayMatchingResult::getOverrideReason).orElse(null),
                lines);
    }

    private MatchingSummaryDTO toSummary(ThreeWayMatchingResult r) {
        Invoice inv = r.getInvoice();
        PurchaseOrder po = r.getPurchaseOrder();
        int lineCount = inv.getItems() == null ? 0 : inv.getItems().size();
        return new MatchingSummaryDTO(
                inv.getId(), inv.getReferenceNumber(), inv.getSupplierName(),
                po != null ? po.getId() : null, po != null ? po.getPoNumber() : null,
                r.getGoodsReceiptNote() != null,
                r.getStatus(), lineCount, 0, r.getCreatedAt());
    }

    private MatchingSummaryDTO toSummaryFrom(Invoice inv, PurchaseOrder po, List<GoodsReceiptNote> grns,
                                             List<LineComparison> lines, ThreeWayMatchingResult last) {
        int discrepancies = (int) lines.stream()
                .filter(l -> l.verdict() == LineVerdict.MISMATCH || l.verdict() == LineVerdict.MISSING_IN_PO)
                .count();
        return new MatchingSummaryDTO(
                inv.getId(), inv.getReferenceNumber(), inv.getSupplierName(),
                po.getId(), po.getPoNumber(), !grns.isEmpty(),
                last != null ? last.getStatus() : null,
                inv.getItems().size(), discrepancies,
                last != null ? last.getCreatedAt() : null);
    }

    private BigDecimal variancePct(BigDecimal actual, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) return null;
        return actual.subtract(base).abs()
                .multiply(new BigDecimal("100"))
                .divide(base, 2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
