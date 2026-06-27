package com.oct.invoicesystem.domain.payment.model;

/**
 * Cycle de vie d'un paiement.
 * SCHEDULED  : paiement planifié, pas encore exécuté (la facture reste BON_A_PAYER).
 * PROCESSED  : paiement exécuté (déclenche la finalisation : remittance + PAYE + ARCHIVE).
 */
public enum PaymentStatus {
    SCHEDULED,
    PROCESSED
}
