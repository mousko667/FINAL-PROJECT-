package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.purchasing.dto.LineVerdict;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Logique de tolérance de rapprochement, réutilisée par le matching d'écriture et la lecture ligne-à-ligne. */
@Component
public class MatchingComparator {

    /**
     * True si l'écart qté/prix d'une ligne reste dans la tolérance (% OU montant) de la config.
     *
     * @param invoiceQty quantity on the invoice
     * @param poQty quantity on the purchase order
     * @param invoicePrice unit price on the invoice
     * @param poPrice unit price on the purchase order
     * @param config the matching tolerance configuration
     * @return true if the variance is within tolerance
     */
    public boolean isWithinTolerance(BigDecimal invoiceQty, BigDecimal poQty,
                                     BigDecimal invoicePrice, BigDecimal poPrice, MatchingConfig config) {
        BigDecimal percTolerance = config.getTolerancePercentage();
        BigDecimal amtTolerance = config.getToleranceAmount();

        BigDecimal qtyVariance = invoiceQty.subtract(poQty).abs();
        BigDecimal priceVariance = invoicePrice.subtract(poPrice).abs();

        BigDecimal qtyThreshold = poQty.multiply(percTolerance).divide(new BigDecimal("100"));
        BigDecimal qtyVarianceAmount = qtyVariance.multiply(poPrice);
        if (qtyVariance.compareTo(qtyThreshold) > 0 && qtyVarianceAmount.compareTo(amtTolerance) > 0) {
            return false;
        }

        BigDecimal priceThreshold = poPrice.multiply(percTolerance).divide(new BigDecimal("100"));
        BigDecimal priceVarianceAmount = priceVariance.multiply(poQty);
        if (priceVariance.compareTo(priceThreshold) > 0 && priceVarianceAmount.compareTo(amtTolerance) > 0) {
            return false;
        }
        return true;
    }

    /**
     * Verdict d'une ligne dont l'équivalent PO existe.
     *
     * @param invoiceQty quantity on the invoice
     * @param invoicePrice unit price on the invoice
     * @param poQty quantity on the purchase order
     * @param poPrice unit price on the purchase order
     * @param config the matching tolerance configuration
     * @return MATCHED if exact match, WITHIN_TOLERANCE if variance is acceptable, MISMATCH otherwise
     */
    public LineVerdict verdictForLine(BigDecimal invoiceQty, BigDecimal invoicePrice,
                                      BigDecimal poQty, BigDecimal poPrice, MatchingConfig config) {
        if (invoiceQty.compareTo(poQty) == 0 && invoicePrice.compareTo(poPrice) == 0) {
            return LineVerdict.MATCHED;
        }
        return isWithinTolerance(invoiceQty, poQty, invoicePrice, poPrice, config)
                ? LineVerdict.WITHIN_TOLERANCE : LineVerdict.MISMATCH;
    }
}
