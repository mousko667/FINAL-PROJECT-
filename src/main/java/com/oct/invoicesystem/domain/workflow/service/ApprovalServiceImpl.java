package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;
import com.oct.invoicesystem.domain.workflow.dto.ApprovalStepResponse;
import com.oct.invoicesystem.domain.workflow.dto.ValidatorStatsResponse;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalDelegationRepository;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import com.oct.invoicesystem.shared.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalStepRepository approvalStepRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceStateMachineService invoiceStateMachineService;
    private final ApprovalDelegationRepository delegationRepository;

    @Override
    @Transactional
    public void assignReviewer(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() == InvoiceStatus.EN_CONTROLE_AA) {
            checkRole(currentUser, invoice.getDepartment().getN1Role());
            createOrUpdateStep(invoice, 1, currentUser, "Validation N1 - " + invoice.getDepartment().getCode(), null, null, ApprovalStepStatus.PENDING);
            invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.ASSIGN_REVIEWER, null);
        } else if (invoice.getStatus() == InvoiceStatus.SOUMIS) {
            throw new WorkflowException("error.approval.aa_control_required");
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N1 && invoice.getDepartment().isRequiresN2()) {
            throw new WorkflowException("error.approval.cannot_assign_n2_in_n1");
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N2) {
            checkRole(currentUser, invoice.getDepartment().getN2Role());
            createOrUpdateStep(invoice, 2, currentUser, "Validation N2 - " + invoice.getDepartment().getCode(), null, null, ApprovalStepStatus.PENDING);
            // No state machine event for this
        } else {
            throw new WorkflowException("error.approval.cannot_assign_from_state");
        }
    }

    /**
     * Moves a SOUMIS invoice into EN_CONTROLE_AA: the mandatory Assistant Comptable (AA)
     * control step that precedes N1 validation (separation of duties). Only a user holding
     * ROLE_ASSISTANT_COMPTABLE may perform this action, and the AA cannot control an invoice
     * they themselves submitted.
     */
    @Override
    @Transactional
    public void assignAA(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() != InvoiceStatus.SOUMIS) {
            throw new WorkflowException("error.approval.not_in_soumis_state");
        }
        checkRole(currentUser, "ROLE_ASSISTANT_COMPTABLE");
        ensureNotSubmitter(invoice, currentUser);
        createOrUpdateStep(invoice, 0, currentUser, "Controle AA", null, null, ApprovalStepStatus.PENDING);
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.ASSIGN_AA, null);
    }

    @Override
    @Transactional
    public void validateN1(UUID invoiceId, String comment) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() != InvoiceStatus.EN_VALIDATION_N1) {
            throw new WorkflowException("error.approval.not_in_n1_state");
        }
        checkRole(currentUser, invoice.getDepartment().getN1Role());
        ensureNotSubmitter(invoice, currentUser);
        ensureWithinApprovalLimit(currentUser, invoice);

        createOrUpdateStep(invoice, 1, currentUser, "Validation N1 - " + invoice.getDepartment().getCode(), comment, null, ApprovalStepStatus.APPROVED);
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.VALIDATE_N1, Map.of("comment", comment != null ? comment : ""));
    }

    @Override
    @Transactional
    public void validateN2(UUID invoiceId, String comment) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() != InvoiceStatus.EN_VALIDATION_N2) {
            throw new WorkflowException("error.approval.not_in_n2_state");
        }
        checkRole(currentUser, invoice.getDepartment().getN2Role());
        ensureNotSubmitter(invoice, currentUser);
        ensureNotN1Approver(invoiceId, currentUser);
        ensureWithinApprovalLimit(currentUser, invoice);

        createOrUpdateStep(invoice, 2, currentUser, "Validation N2 - " + invoice.getDepartment().getCode(), comment, null, ApprovalStepStatus.APPROVED);
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.VALIDATE_N2, Map.of("comment", comment != null ? comment : ""));
    }

    @Override
    @Transactional
    public void bonAPayer(UUID invoiceId, String comment) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() != InvoiceStatus.VALIDE) {
            throw new WorkflowException("error.approval.not_ready_for_daf");
        }
        checkRole(currentUser, "ROLE_DAF");
        ensureNotSubmitter(invoice, currentUser);
        
        // P3-09: DAF step is always step_order 3 regardless of department
        createOrUpdateStep(invoice, 3, currentUser, "Bon à Payer", comment, null, ApprovalStepStatus.APPROVED);
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.BON_A_PAYER, Map.of("comment", comment != null ? comment : ""));
    }

    @Override
    @Transactional
    public void reject(UUID invoiceId, String rejectionReason) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        int stepOrder;
        String stepName;
        String requiredRole;
        if (invoice.getStatus() == InvoiceStatus.EN_CONTROLE_AA) {
            stepOrder = 0;
            stepName = "Controle AA";
            requiredRole = "ROLE_ASSISTANT_COMPTABLE";
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N1) {
            stepOrder = 1;
            stepName = "Validation N1 - " + invoice.getDepartment().getCode();
            requiredRole = invoice.getDepartment().getN1Role();
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N2) {
            stepOrder = 2;
            stepName = "Validation N2 - " + invoice.getDepartment().getCode();
            requiredRole = invoice.getDepartment().getN2Role();
        } else if (invoice.getStatus() == InvoiceStatus.VALIDE) {
            stepOrder = 3;
            stepName = "Bon à Payer";
            requiredRole = "ROLE_DAF";
        } else {
            throw new WorkflowException("Cannot reject invoice in state " + invoice.getStatus());
        }
        checkRole(currentUser, requiredRole);

        createOrUpdateStep(invoice, stepOrder, currentUser, stepName, null, rejectionReason, ApprovalStepStatus.REJECTED);
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.REJECT, Map.of("rejectionReason", rejectionReason != null ? rejectionReason : ""));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalStepResponse> getApprovalSteps(UUID invoiceId) {
        return approvalStepRepository.findByInvoiceIdOrderByStepOrderAsc(invoiceId).stream()
                .map(s -> new ApprovalStepResponse(
                        s.getId(),
                        s.getStepOrder(),
                        s.getStepNameEn(),
                        s.getStepNameFr(),
                        s.getDepartmentCode(),
                        s.getStatus(),
                        s.getApprover() != null ? s.getApprover().getUsername() : null,
                        s.getApprover() != null
                                ? (s.getApprover().getFirstName() + " " + s.getApprover().getLastName()).trim()
                                : null,
                        s.getComments(),
                        s.getRejectionReason(),
                        s.getDeadline(),
                        s.getActionAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ValidatorStatsResponse getValidatorStats(UUID approverId) {
        long approvedTotal = approvalStepRepository.countByApproverIdAndStatus(approverId, ApprovalStepStatus.APPROVED);
        Instant startOfMonth = LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
        long processedThisMonth = approvalStepRepository.countByApproverIdAndStatusInAndActionAtGreaterThanEqual(
                approverId, List.of(ApprovalStepStatus.APPROVED, ApprovalStepStatus.REJECTED), startOfMonth);
        return new ValidatorStatsResponse(approvedTotal, processedThisMonth);
    }

    private ApprovalStep createOrUpdateStep(Invoice invoice, int stepOrder, User approver, String nameFr, String comment, String rejButtonReason, ApprovalStepStatus status) {
        ApprovalStep step = approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), stepOrder)
                .orElse(ApprovalStep.builder()
                        .invoice(invoice)
                        .stepOrder(stepOrder)
                        .departmentCode(invoice.getDepartment().getCode())
                        .stepNameFr(nameFr)
                        .stepNameEn(nameFr)
                        .deadline(DateUtils.addBusinessDays(Instant.now(), 3))
                        .build());

        step.setApprover(approver);
        step.setStatus(status);
        if (comment != null) step.setComments(comment);
        if (rejButtonReason != null) step.setRejectionReason(rejButtonReason);
        if (status != ApprovalStepStatus.PENDING) {
            step.setActionAt(Instant.now());
        }
        
        return approvalStepRepository.save(step);
    }

    private Invoice getInvoice(UUID id) {
        return invoiceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with ID: " + id));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User userPrincipal) {
            return userPrincipal;
        }
        throw new WorkflowException("error.approval.no_authenticated_user");
    }

    private void checkRole(User user, String requiredRole) {
        if (requiredRole == null) return;
        boolean hasRole = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(requiredRole));
        if (hasRole) return;

        // Check if user has an active delegation covering this role's department
        // Department code is embedded in roles like ROLE_VALIDATEUR_N1_INFO or ROLE_DAF
        String deptCode = requiredRole.replaceAll("^ROLE_(VALIDATEUR_N[12]_)?", "");
        List<ApprovalDelegation> delegations =
                delegationRepository.findActiveDelegationsForDelegatee(user.getId(), LocalDate.now());
        boolean hasDelegation = delegations.stream()
                .anyMatch(d -> d.getDepartmentCode().equals(deptCode));
        if (hasDelegation) return;

        throw new AccessDeniedException("User does not have required role: " + requiredRole
                + " and has no active delegation for department: " + deptCode);
    }

    private void ensureNotSubmitter(Invoice invoice, User approver) {
        if (invoice.getSubmittedBy() != null
                && approver != null
                && invoice.getSubmittedBy().getId().equals(approver.getId())) {
            throw new WorkflowException("error.approval.approver_is_submitter");
        }
    }

    private void ensureNotN1Approver(UUID invoiceId, User approver) {
        ApprovalStep n1Step = approvalStepRepository.findByInvoiceIdAndStepOrder(invoiceId, 1).orElse(null);
        if (n1Step != null && n1Step.getApprover() != null && approver != null 
                && n1Step.getApprover().getId().equals(approver.getId())) {
            throw new WorkflowException("error.approval.n2_same_as_n1");
        }
    }

    private void ensureWithinApprovalLimit(User approver, Invoice invoice) {
        java.math.BigDecimal limit = approver.getApprovalLimit();
        if (limit == null) {
            return; // null = unlimited
        }
        if (invoice.getAmount() != null && limit.compareTo(invoice.getAmount()) < 0) {
            throw new WorkflowException("approval.limit.exceeded");
        }
    }
}
