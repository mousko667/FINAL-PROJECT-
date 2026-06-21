package com.oct.invoicesystem.domain.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tendance temporelle volume (nb de factures) et valeur (montant total) par mois,
 * agrégée sur la date de facture (issueDate). Voir M11 #7 / feature #6.
 */
public record VolumeTrendDTO(
        LocalDate fromDate,
        LocalDate toDate,
        List<MonthlyTrendPoint> points
) {
    /** Un point mensuel : label YYYY-MM, année/mois, nombre de factures et montant total. */
    public record MonthlyTrendPoint(
            String monthLabel,
            int year,
            int month,
            long invoiceCount,
            BigDecimal totalAmount
    ) {}
}
