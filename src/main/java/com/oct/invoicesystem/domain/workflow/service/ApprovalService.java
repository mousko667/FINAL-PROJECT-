package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.workflow.dto.ApprovalStepResponse;
import com.oct.invoicesystem.domain.workflow.dto.ValidatorStatsResponse;

import java.util.List;
import java.util.UUID;

public interface ApprovalService {
    void assignReviewer(UUID invoiceId);

    /**
     * Moves a SOUMIS invoice into EN_CONTROLE_AA: the mandatory Assistant Comptable (AA)
     * control step that must happen before any N1 reviewer can be assigned (separation of
     * duties). Only a user holding ROLE_ASSISTANT_COMPTABLE may perform this action, and the
     * AA cannot control an invoice they themselves submitted.
     *
     * @param invoiceId the invoice to move from SOUMIS to EN_CONTROLE_AA
     */
    void assignAA(UUID invoiceId);
    void validateN1(UUID invoiceId, String comment);
    void validateN2(UUID invoiceId, String comment);
    void bonAPayer(UUID invoiceId, String comment);
    void reject(UUID invoiceId, String rejectionReason);

    /**
     * Archive une facture payee (PAYE -> ARCHIVE). Action documentaire explicite, distincte du
     * paiement depuis AUDIT-030 (D3) : le reglement laisse la facture au statut PAYE, etat de
     * repos observable, et l'archivage releve d'une decision de gestion documentaire.
     *
     * @param invoiceId la facture a archiver ; elle doit etre au statut PAYE
     */
    void archive(UUID invoiceId);
    List<ApprovalStepResponse> getApprovalSteps(UUID invoiceId);
    ValidatorStatsResponse getValidatorStats(UUID approverId);
}
