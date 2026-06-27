package com.oct.invoicesystem.domain.report.dto;

/**
 * Analyse du cycle de paiement sur une periode (basee sur la date d'execution reelle).
 * Les delais moyens sont en jours ; null si l'echantillon correspondant est vide.
 */
public record PaymentCycleReportDTO(
        long invoicesPaidCount,
        Double avgSubmissionToBapDays,
        Double avgBapToPaymentDays,
        Double avgScheduledToProcessedDays,
        Double avgTotalCycleDays
) {}
