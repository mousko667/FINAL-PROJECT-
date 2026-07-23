package com.oct.invoicesystem.domain.invoice.statemachine;

/**
 * Keys for variables stored in the state machine extended state.
 */
public final class WorkflowExtendedStateKeys {

    public static final String DEPARTMENT = "department";
    public static final String USER_ID = "USER_ID";
    // AUDIT-030 (D3) : AUTO_ARCHIVE retire. L'archivage n'est plus un effet de bord du paiement
    // mais une action documentaire explicite, gardee par le statut source (PAYE) et non par un
    // drapeau d'appelant. Plus aucun emetteur en production ne posait ce drapeau.
    public static final String CHANGE_REASON = "CHANGE_REASON";

    private WorkflowExtendedStateKeys() {
    }
}
