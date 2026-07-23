package com.oct.invoicesystem.domain.payment.model;

/**
 * Cycle de vie d'un paiement.
 * SCHEDULED  : paiement planifié, pas encore exécuté (la facture reste BON_A_PAYER).
 * PROCESSED  : paiement exécuté (déclenche la finalisation : remittance + passage en PAYE).
 *              L'archivage est une action documentaire distincte (AUDIT-030).
 */
public enum PaymentStatus {
    SCHEDULED,
    PROCESSED
}
