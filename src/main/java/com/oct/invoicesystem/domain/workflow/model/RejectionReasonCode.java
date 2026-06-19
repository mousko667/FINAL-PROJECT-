package com.oct.invoicesystem.domain.workflow.model;

/** Predefined invoice rejection reasons (M4 #8). Labels resolved via MessageSource. */
public enum RejectionReasonCode {
    MONTANT_INCORRECT,
    PIECE_MANQUANTE,
    DOUBLON,
    INFOS_FOURNISSEUR_INCORRECTES,
    HORS_BUDGET,
    AUTRE
}
