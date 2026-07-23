package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reference catalogue of the ten invoice business rules of {@code docs/WORKFLOW.md} §8.
 *
 * <p><b>Enforcement note (N1, PROB-118):</b> most rules are enforced at their real entry point, not
 * from this class:
 * <ul>
 *   <li>Rule 1 (document required) → {@code DocumentRequiredGuard} on SUBMIT.</li>
 *   <li>Rule 2 (rejection reason) → {@code RejectionReasonGuard} on REJECT.</li>
 *   <li>Rule 3 (approver ≠ submitter) → {@code ApprovalServiceImpl.ensureNotSubmitter}.</li>
 *   <li><b>Rule 4 (resubmission requires a correction) → wired here via
 *       {@code ResubmissionVersionGuard} on the RESUBMIT transition.</b></li>
 *   <li>Rule 5 (DAF-only actions) → {@code @PreAuthorize("hasRole('DAF')")} on the endpoints.</li>
 *   <li>Rule 6 (assistant creates/submits) → {@code @PreAuthorize} on the invoice endpoints.</li>
 *   <li>Rule 7 (archive is an explicit action on a PAYE invoice) → {@code ApprovalService.archive}
 *       plus the {@code InvoiceStateMachineServiceImpl} ARCHIVE guard (source status must be PAYE).</li>
 *   <li>Rules 8–10 → soft-delete policy, entity {@code BigDecimal} typing, reference generator.</li>
 * </ul>
 * Only {@link #validateResubmissionVersion(Integer, Integer)} is invoked from production code; the
 * remaining methods document the rule contract and back the unit tests, so they must stay in sync
 * with the enforcing components above.
 */
@Service
public class InvoiceValidationService {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("^FAC-\\d{4}-\\d{5}$");
    private static final String ROLE_ASSISTANT_COMPTABLE = "ROLE_ASSISTANT_COMPTABLE";
    private static final String ROLE_DAF = "ROLE_DAF";
    private static final int MIN_REJECTION_REASON_LENGTH = 10;

    /**
     * Rule 1: An invoice cannot be submitted without at least one attached document.
     *
     * @param invoice invoice to validate
     */
    public void validateHasAtLeastOneDocument(Invoice invoice) {
        if (invoice.getDocuments() == null || invoice.getDocuments().isEmpty()) {
            throw new ValidationException("Invoice cannot be submitted without at least one document");
        }
    }

    /**
     * Rule 2: A rejection reason is mandatory and must have at least 10 characters.
     *
     * @param reason rejection reason
     */
    public void validateRejectionReason(String reason) {
        if (reason == null || reason.trim().length() < MIN_REJECTION_REASON_LENGTH) {
            throw new ValidationException("Rejection reason must contain at least 10 characters");
        }
    }

    /**
     * Rule 3: An approver cannot approve their own submitted invoice.
     *
     * @param invoice invoice under approval
     * @param approver actor performing the approval
     */
    public void validateApproverIsNotSubmitter(Invoice invoice, User approver) {
        if (invoice.getSubmittedBy() != null
                && approver != null
                && Objects.equals(invoice.getSubmittedBy().getId(), approver.getId())) {
            throw new WorkflowException("Approver cannot approve their own submitted invoice");
        }
    }

    /**
     * Rule 4: Resubmission requires invoice version increment since rejection.
     *
     * @param currentVersion current invoice version
     * @param rejectedVersion version at rejection time
     */
    public void validateResubmissionVersion(Integer currentVersion, Integer rejectedVersion) {
        if (currentVersion == null || rejectedVersion == null || currentVersion <= rejectedVersion) {
            throw new WorkflowException("Resubmission requires invoice update after rejection");
        }
    }

    /**
     * Rule 5: DAF approves all invoices.
     *
     * @param actor actor attempting DAF approval action
     */
    public void validateDafApprover(User actor) {
        if (!hasRole(actor, ROLE_DAF)) {
            throw new ValidationException("Only DAF can perform this action");
        }
    }

    /**
     * Rule 6: Only ASSISTANT_COMPTABLE can create and submit invoices.
     *
     * @param actor actor performing create/submit
     */
    public void validateAssistantComptable(User actor) {
        if (!hasRole(actor, ROLE_ASSISTANT_COMPTABLE)) {
            throw new ValidationException("Only ASSISTANT_COMPTABLE can create and submit invoices");
        }
    }

    /**
     * Rule 7: Archiving is automatic and only valid from PAYE.
     *
     * @param currentStatus current invoice status
     */
    public void validateArchiveIsAutomatic(InvoiceStatus currentStatus) {
        if (currentStatus != InvoiceStatus.PAYE) {
            throw new WorkflowException("Archive can only be triggered automatically from PAYE");
        }
    }

    /**
     * Rule 8: Soft delete only, hard delete never allowed.
     */
    public void validateSoftDeleteOnly() {
        // Business contract guard: any caller asking for hard delete violates workflow rules.
        throw new WorkflowException("Hard delete is forbidden; use soft delete only");
    }

    /**
     * Rule 9: Monetary amounts must use BigDecimal with non-negative values.
     *
     * @param amount invoice amount
     */
    public void validateMonetaryAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new ValidationException("Amount must be a non-negative BigDecimal");
        }
    }

    /**
     * Rule 10: Reference number must match FAC-{YYYY}-{NNNNN}.
     *
     * @param referenceNumber reference number to validate
     */
    public void validateReferenceFormat(String referenceNumber) {
        if (referenceNumber == null || !REFERENCE_PATTERN.matcher(referenceNumber).matches()) {
            throw new ValidationException("Invalid reference format: FAC-{YYYY}-{NNNNN} expected");
        }
    }

    private boolean hasRole(User actor, String role) {
        if (actor == null || actor.getAuthorities() == null) {
            return false;
        }
        return actor.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
