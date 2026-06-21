package com.oct.invoicesystem.domain.purchasing.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Détail d'un rapprochement avec comparaison ligne-à-ligne PO/GRN/facture. Lecture seule. */
public record MatchingDetailDTO(
        MatchingSummaryDTO summary,
        String discrepancyNotes,
        UUID overriddenBy,
        String overrideReason,
        List<LineComparison> lines) {

    /** Comparaison d'une ligne entre PO, GRN (qté reçue) et facture. */
    public record LineComparison(
            String description,
            BigDecimal poQuantity,
            BigDecimal poUnitPrice,
            BigDecimal receivedQuantity,   // null si pas de GRN
            BigDecimal invoiceQuantity,
            BigDecimal invoiceUnitPrice,
            BigDecimal qtyVariancePct,
            BigDecimal priceVariancePct,
            LineVerdict verdict) {
    }
}
