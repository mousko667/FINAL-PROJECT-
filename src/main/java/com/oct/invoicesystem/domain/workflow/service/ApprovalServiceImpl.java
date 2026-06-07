package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    @Override
    @Transactional
    public void assignReviewer(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() == InvoiceStatus.SOUMIS) {
            checkRole(currentUser, invoice.getDepartment().getN1Role());
            createOrUpdateStep(invoice, 1, currentUser, "Validation N1 - " + invoice.getDepartment().getCode(), null, null, ApprovalStepStatus.PENDING);
            invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.ASSIGN_REVIEWER, null);
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N1 && invoice.getDepartment().isRequiresN2()) {
            // this handles if N2 assigns while in N1? No, N2 only assigns when EN_VALIDATION_N2
            throw new WorkflowException("Cannot assign N2 while still in N1 validation");
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N2) {
            checkRole(currentUser, invoice.getDepartment().getN2Role());
            createOrUpdateStep(invoice, 2, currentUser, "Validation N2 - " + invoice.getDepartment().getCode(), null, null, ApprovalStepStatus.PENDING);
            // No state machine event for this
        } else {
            throw new WorkflowException("Cannot assign reviewer from state " + invoice.getStatus());
        }
    }

    @Override
    @Transactional
    public void validateN1(UUID invoiceId, String comment) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() != InvoiceStatus.EN_VALIDATION_N1) {
            throw new WorkflowException("Invoice is not in N1 validation state");
        }
        checkRole(currentUser, invoice.getDepartment().getN1Role());
        ensureNotSubmitter(invoice, currentUser);
        
        createOrUpdateStep(invoice, 1, currentUser, "Validation N1 - " + invoice.getDepartment().getCode(), comment, null, ApprovalStepStatus.APPROVED);
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.VALIDATE_N1, Map.of("comment", comment != null ? comment : ""));
    }

    @Override
    @Transactional
    public void validateN2(UUID invoiceId, String comment) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() != InvoiceStatus.EN_VALIDATION_N2) {
            throw new WorkflowException("Invoice is not in N2 validation state");
        }
        checkRole(currentUser, invoice.getDepartment().getN2Role());
        ensureNotSubmitter(invoice, currentUser);

        createOrUpdateStep(invoice, 2, currentUser, "Validation N2 - " + invoice.getDepartment().getCode(), comment, null, ApprovalStepStatus.APPROVED);
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.VALIDATE_N2, Map.of("comment", comment != null ? comment : ""));
    }

    @Override
    @Transactional
    public void bonAPayer(UUID invoiceId, String comment) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() != InvoiceStatus.VALIDE) {
            throw new WorkflowException("Invoice is not ready for DAF approval (status must be VALIDE)");
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
        if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N1) {
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
    public List<Map<String, Object>> getApprovalSteps(UUID invoiceId) {
        List<ApprovalStep> steps = approvalStepRepository.findByInvoiceIdOrderByStepOrderAsc(invoiceId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ApprovalStep s : steps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("stepOrder", s.getStepOrder());
            m.put("stepName", s.getStepNameEn());
            m.put("stepNameFr", s.getStepNameFr());
            m.put("departmentCode", s.getDepartmentCode());
            m.put("status", s.getStatus());
            m.put("approverUsername", s.getApprover() != null ? s.getApprover().getUsername() : null);
            m.put("approverName", s.getApprover() != null
                    ? (s.getApprover().getFirstName() + " " + s.getApprover().getLastName()).trim()
                    : null);
            m.put("comments", s.getComments());
            m.put("rejectionReason", s.getRejectionReason());
            m.put("deadline", s.getDeadline());
            m.put("actionAt", s.getActionAt());
            result.add(m);
        }
        return result;
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
        throw new WorkflowException("No authenticated user found");
    }

    private void checkRole(User user, String requiredRole) {
        if (requiredRole == null) return;
        boolean hasRole = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(requiredRole));
        if (!hasRole) {
            throw new AccessDeniedException("User does not have required role: " + requiredRole);
        }
    }

    private void ensureNotSubmitter(Invoice invoice, User approver) {
        if (invoice.getSubmittedBy() != null
                && approver != null
                && invoice.getSubmittedBy().getId().equals(approver.getId())) {
            throw new WorkflowException("Approver cannot approve their own submitted invoice");
        }
    }
}
