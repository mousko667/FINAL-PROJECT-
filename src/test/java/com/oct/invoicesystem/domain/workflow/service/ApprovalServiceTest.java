package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.workflow.dto.ApprovalStepResponse;
import com.oct.invoicesystem.domain.workflow.dto.ValidatorStatsResponse;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalDelegationRepository;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalStepRepository approvalStepRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceStateMachineService invoiceStateMachineService;

    @Mock
    private ApprovalDelegationRepository delegationRepository;

    @InjectMocks
    private ApprovalServiceImpl approvalService;

    private Invoice invoice;
    private User currentUser;
    private Department department;

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setId(UUID.randomUUID());
        department.setCode("INFO");
        department.setRequiresN2(true);
        department.setN1Role("ROLE_VALIDATEUR_N1_INFO");
        department.setN2Role("ROLE_VALIDATEUR_N2_INFO");

        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStatus(InvoiceStatus.SOUMIS);
        invoice.setDepartment(department);

        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setUsername("approver");
    }

    private void mockSecurityContext(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        userRole.setUser(currentUser);
        currentUser.setUserRoles(Set.of(userRole));
        
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "pass", currentUser.getAuthorities())
        );
    }

    @Test
    void assignReviewer_WhenSoumis_WithCorrectRole_SuccessAndSetsDeadline() {
        mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), 1)).thenReturn(Optional.empty());
        when(approvalStepRepository.save(any(ApprovalStep.class))).thenAnswer(i -> i.getArguments()[0]);

        approvalService.assignReviewer(invoice.getId());

        ArgumentCaptor<ApprovalStep> stepCaptor = ArgumentCaptor.forClass(ApprovalStep.class);
        verify(approvalStepRepository).save(stepCaptor.capture());
        ApprovalStep savedStep = stepCaptor.getValue();
        
        assertEquals(1, savedStep.getStepOrder());
        assertEquals(ApprovalStepStatus.PENDING, savedStep.getStatus());
        assertEquals(currentUser, savedStep.getApprover());
        assertNotNull(savedStep.getDeadline()); // verifies deadline check logic
        
        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.ASSIGN_REVIEWER), isNull());
    }

    @Test
    void assignReviewer_WhenSoumis_WithWrongRole_ThrowsAccessDenied() {
        mockSecurityContext("ROLE_WRONG");
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        // Wrong role falls through to the delegation lookup; no active delegation → AccessDenied
        when(delegationRepository.findActiveDelegationsForDelegatee(any(), any())).thenReturn(List.of());

        assertThrows(AccessDeniedException.class, () -> approvalService.assignReviewer(invoice.getId()));
    }

    @Test
    void assignReviewer_WhenValide_ThrowsWorkflowException() {
        mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
        invoice.setStatus(InvoiceStatus.VALIDE);
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        WorkflowException ex = assertThrows(WorkflowException.class, () -> approvalService.assignReviewer(invoice.getId()));
        assertTrue(ex.getMessage().contains("Cannot assign reviewer from state"));
    }

    @Test
    void validateN1_Success() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        
        ApprovalStep existingStep = new ApprovalStep();
        existingStep.setStatus(ApprovalStepStatus.PENDING);
        when(approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), 1)).thenReturn(Optional.of(existingStep));
        when(approvalStepRepository.save(any(ApprovalStep.class))).thenAnswer(i -> i.getArguments()[0]);

        approvalService.validateN1(invoice.getId(), "Looks good N1");

        verify(approvalStepRepository).save(existingStep);
        assertEquals(ApprovalStepStatus.APPROVED, existingStep.getStatus());
        assertEquals("Looks good N1", existingStep.getComments());
        assertNotNull(existingStep.getActionAt());

        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.VALIDATE_N1), anyMap());
    }

    @Test
    void validateN1_WrongState_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.SOUMIS);
        mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThrows(WorkflowException.class, () -> approvalService.validateN1(invoice.getId(), "comment"));
    }

    @Test
    void validateN2_Success() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N2);
        mockSecurityContext("ROLE_VALIDATEUR_N2_INFO");
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        
        ApprovalStep existingStep = new ApprovalStep();
        when(approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), 2)).thenReturn(Optional.of(existingStep));
        when(approvalStepRepository.save(any(ApprovalStep.class))).thenAnswer(i -> i.getArguments()[0]);

        approvalService.validateN2(invoice.getId(), "Looks good N2");

        verify(approvalStepRepository).save(existingStep);
        assertEquals(ApprovalStepStatus.APPROVED, existingStep.getStatus());
        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.VALIDATE_N2), anyMap());
    }

    @Test
    void bonAPayer_Success() {
        invoice.setStatus(InvoiceStatus.VALIDE);
        mockSecurityContext("ROLE_DAF");
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        
        ApprovalStep existingStep = new ApprovalStep();
        // DAF is step order 3
        when(approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), 3)).thenReturn(Optional.of(existingStep));
        when(approvalStepRepository.save(any(ApprovalStep.class))).thenAnswer(i -> i.getArguments()[0]);

        approvalService.bonAPayer(invoice.getId(), "BAP OK");

        verify(approvalStepRepository).save(existingStep);
        assertEquals(ApprovalStepStatus.APPROVED, existingStep.getStatus());
        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.BON_A_PAYER), anyMap());
    }

    @Test
    void reject_FromN1_Success() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        
        ApprovalStep existingStep = new ApprovalStep();
        when(approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), 1)).thenReturn(Optional.of(existingStep));
        when(approvalStepRepository.save(any(ApprovalStep.class))).thenAnswer(i -> i.getArguments()[0]);

        approvalService.reject(invoice.getId(), "Rejected due to missing info");

        verify(approvalStepRepository).save(existingStep);
        assertEquals(ApprovalStepStatus.REJECTED, existingStep.getStatus());
        assertEquals("Rejected due to missing info", existingStep.getRejectionReason());
        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.REJECT), anyMap());
    }

    @Test
    void reject_FromInvalidState_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.SOUMIS);
        mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThrows(WorkflowException.class, () -> approvalService.reject(invoice.getId(), "reason"));
    }

    @Test
    void getApprovalSteps_mapsEntityFieldsToTypedDto() {
        UUID invoiceId = UUID.randomUUID();
        User approver = new User();
        approver.setUsername("validator");
        approver.setFirstName("Jean");
        approver.setLastName("Dupont");
        Instant deadline = Instant.now().plusSeconds(86_400);
        Instant actionAt = Instant.now();
        ApprovalStep step = ApprovalStep.builder()
                .id(UUID.randomUUID())
                .stepOrder(1)
                .stepNameEn("Validation N1")
                .stepNameFr("Validation N1 (fr)")
                .departmentCode("INFO")
                .status(ApprovalStepStatus.APPROVED)
                .approver(approver)
                .comments("ok")
                .deadline(deadline)
                .actionAt(actionAt)
                .build();
        when(approvalStepRepository.findByInvoiceIdOrderByStepOrderAsc(invoiceId)).thenReturn(List.of(step));

        List<ApprovalStepResponse> result = approvalService.getApprovalSteps(invoiceId);

        assertEquals(1, result.size());
        ApprovalStepResponse r = result.get(0);
        assertEquals(step.getId(), r.id());
        assertEquals(1, r.stepOrder());
        assertEquals("Validation N1", r.stepName());          // mapped from stepNameEn
        assertEquals("Validation N1 (fr)", r.stepNameFr());
        assertEquals("INFO", r.departmentCode());
        assertEquals(ApprovalStepStatus.APPROVED, r.status());
        assertEquals("validator", r.approverUsername());
        assertEquals("Jean Dupont", r.approverName());        // first + last concatenated
        assertEquals("ok", r.comments());
        assertEquals(deadline, r.deadline());
        assertEquals(actionAt, r.actionAt());
    }

    @Test
    void getValidatorStats_countsApprovedTotalAndProcessedThisMonth() {
        UUID approverId = UUID.randomUUID();
        when(approvalStepRepository.countByApproverIdAndStatus(approverId, ApprovalStepStatus.APPROVED))
                .thenReturn(7L);
        when(approvalStepRepository.countByApproverIdAndStatusInAndActionAtGreaterThanEqual(
                eq(approverId), any(), any())).thenReturn(3L);

        ValidatorStatsResponse stats = approvalService.getValidatorStats(approverId);

        assertEquals(7L, stats.approvedTotal());
        assertEquals(3L, stats.processedThisMonth());
    }

    @Test
    void getApprovalSteps_nullApprover_yieldsNullUsernameAndName() {
        UUID invoiceId = UUID.randomUUID();
        ApprovalStep step = ApprovalStep.builder()
                .id(UUID.randomUUID())
                .stepOrder(2)
                .stepNameEn("Validation N2")
                .stepNameFr("Validation N2 (fr)")
                .departmentCode("INFO")
                .status(ApprovalStepStatus.PENDING)
                .approver(null)
                .build();
        when(approvalStepRepository.findByInvoiceIdOrderByStepOrderAsc(invoiceId)).thenReturn(List.of(step));

        List<ApprovalStepResponse> result = approvalService.getApprovalSteps(invoiceId);

        assertNull(result.get(0).approverUsername());
        assertNull(result.get(0).approverName());
        assertEquals(ApprovalStepStatus.PENDING, result.get(0).status());
    }
}
