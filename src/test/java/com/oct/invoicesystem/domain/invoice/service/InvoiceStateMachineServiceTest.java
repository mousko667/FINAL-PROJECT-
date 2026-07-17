package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.notification.event.listener.EmailNotificationListener;
import com.oct.invoicesystem.domain.notification.event.listener.PersistNotificationListener;
import com.oct.invoicesystem.domain.notification.event.listener.WebSocketNotificationListener;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.repository.GoodsReceiptNoteRepository;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.webhook.event.listener.WebhookEventPublisher;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceStateMachineServiceTest {

    @Autowired
    private InvoiceStateMachineService invoiceStateMachineService;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private InvoiceStatusHistoryRepository invoiceStatusHistoryRepository;

    // Neutralize @Async @EventListener beans so they never touch shared mocks on background threads
    @MockBean
    private PersistNotificationListener persistNotificationListener;

    @MockBean
    private EmailNotificationListener emailNotificationListener;

    @MockBean
    private WebSocketNotificationListener webSocketNotificationListener;

    @MockBean
    private WebhookEventPublisher webhookEventPublisher;

    @MockBean
    private PurchaseOrderRepository purchaseOrderRepository;

    @MockBean
    private GoodsReceiptNoteRepository goodsReceiptNoteRepository;

    @MockBean
    private ThreeWayMatchingResultRepository matchingResultRepository;

    private Invoice invoice;
    private User currentUser;
    private Department department;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setId(UUID.randomUUID());
        department.setRequiresN2(true);
        department.setN1Role("ROLE_VALIDATEUR_N1_INFO");
        department.setN2Role("ROLE_VALIDATEUR_N2_INFO");

        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStatus(InvoiceStatus.BROUILLON);
        invoice.setDepartment(department);

        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setUsername("testuser");
        currentUser.setUserRoles(Set.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "pass", currentUser.getAuthorities())
        );

        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
    }

    private void mockUserRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        userRole.setUser(currentUser);
        currentUser.setUserRoles(Set.of(userRole));
        
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "pass", currentUser.getAuthorities())
        );
        when(userRepository.findById(currentUser.getId())).thenReturn(Optional.of(currentUser));
    }

    @Test
    void submit_WithoutDocuments_ThrowsWorkflowException() {
        invoice.setDocuments(List.of());
        
        assertThrows(WorkflowException.class, () -> 
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.SUBMIT, null)
        );
    }

    @Test
    void submit_WithDocuments_Success() {
        invoice.setDocuments(List.of(new InvoiceDocument()));
        
        // Allowed regardless of role initially but let's assume valid state
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.SUBMIT, null);
    }

    @Test
    void submit_WithPurchaseOrderAndUnevaluableMatching_ThrowsWorkflowException() {
        // MAJEUR-3: an invoice with a purchaseOrderId whose matching check cannot be
        // evaluated (e.g. no active matching configuration — a ValidationException,
        // NOT a WorkflowException) must NOT be silently swallowed and allowed to
        // proceed to SOUMIS. Submission must fail closed.
        invoice.setDocuments(List.of(new InvoiceDocument()));
        UUID purchaseOrderId = UUID.randomUUID();
        invoice.setPurchaseOrderId(purchaseOrderId);

        when(matchingResultRepository.findByInvoiceId(invoice.getId())).thenReturn(Optional.empty());

        PurchaseOrder po = PurchaseOrder.builder()
                .id(purchaseOrderId)
                .items(List.of())
                .build();
        when(purchaseOrderRepository.findByIdActive(purchaseOrderId)).thenReturn(Optional.of(po));
        when(goodsReceiptNoteRepository.findByPurchaseOrderId(purchaseOrderId)).thenReturn(List.of());

        // No MatchingConfig is seeded in the test H2 DB, so the real ThreeWayMatchingService
        // bean throws ValidationException("No active matching configuration found") here —
        // a non-WorkflowException that must not be swallowed.
        assertThrows(WorkflowException.class, () ->
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.SUBMIT, null)
        );
    }

    @Test
    void assignReviewer_WithCorrectRole_Success() {
        // AA control is now mandatory before ASSIGN_REVIEWER: the invoice must already be
        // in EN_CONTROLE_AA (assigned via ASSIGN_AA) before an N1 reviewer can be assigned.
        invoice.setStatus(InvoiceStatus.EN_CONTROLE_AA);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO");

        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ASSIGN_REVIEWER, null);
    }

    @Test
    void assignReviewer_WithWrongRole_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.EN_CONTROLE_AA);
        mockUserRole("ROLE_ASSISTANT_COMPTABLE");

        assertThrows(WorkflowException.class, () ->
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ASSIGN_REVIEWER, null)
        );
    }

    @Test
    void assignReviewer_FromSoumisDirectly_ThrowsWorkflowException() {
        // AA control is mandatory: SOUMIS no longer transitions directly to EN_VALIDATION_N1.
        invoice.setStatus(InvoiceStatus.SOUMIS);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO");

        assertThrows(WorkflowException.class, () ->
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ASSIGN_REVIEWER, null)
        );
    }

    @Test
    void assignAa_WithCorrectRole_Success() {
        invoice.setStatus(InvoiceStatus.SOUMIS);
        mockUserRole("ROLE_ASSISTANT_COMPTABLE");

        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ASSIGN_AA, null);
    }

    @Test
    void assignAa_WithWrongRole_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.SOUMIS);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO");

        assertThrows(WorkflowException.class, () ->
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ASSIGN_AA, null)
        );
    }

    @Test
    void validateN1_ToN2_RequiresN2True() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        department.setRequiresN2(true);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO");

        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.VALIDATE_N1, null);
        // We know it routes to N2 because there's no error 
    }

    @Test
    void validateN1_ToValide_RequiresN2False() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        department.setRequiresN2(false);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO");

        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.VALIDATE_N1, null);
    }

    @Test
    void reject_WithoutReason_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO");

        Map<String, Object> vars = new HashMap<>(); // missing rejectionReason
        assertThrows(WorkflowException.class, () -> 
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.REJECT, vars)
        );
    }

    @Test
    void reject_WithShortReason_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO");

        Map<String, Object> vars = new HashMap<>();
        vars.put("rejectionReason", "short");
        assertThrows(WorkflowException.class, () -> 
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.REJECT, vars)
        );
    }

    @Test
    void reject_WithValidReason_Success() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO");

        Map<String, Object> vars = new HashMap<>();
        vars.put("rejectionReason", "Valid rejection reason 10+");
        
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.REJECT, vars);
    }

    @Test
    void bonAPayer_WithWrongRole_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.VALIDE);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO"); // should be DAF

        assertThrows(WorkflowException.class, () -> 
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.BON_A_PAYER, null)
        );
    }

    @Test
    void bonAPayer_WithDafRole_Success() {
        invoice.setStatus(InvoiceStatus.VALIDE);
        mockUserRole("ROLE_DAF");

        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.BON_A_PAYER, null);
    }

    @Test
    void invalidTransition_FromBrouillonToValide_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.BROUILLON);

        assertThrows(WorkflowException.class, () -> 
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.VALIDATE_N1, null)
        );
    }

    @Test
    void validateN2_WithCorrectRole_Success() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N2);
        department.setRequiresN2(true);
        mockUserRole("ROLE_VALIDATEUR_N2_INFO");

        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.VALIDATE_N2, null);
    }

    @Test
    void validateN2_WithWrongRole_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N2);
        department.setRequiresN2(true);
        mockUserRole("ROLE_VALIDATEUR_N1_INFO"); // wrong role

        assertThrows(WorkflowException.class, () -> 
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.VALIDATE_N2, null)
        );
    }

    @Test
    void reject_FromN2_Success() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N2);
        mockUserRole("ROLE_VALIDATEUR_N2_INFO");

        Map<String, Object> vars = new HashMap<>();
        vars.put("rejectionReason", "Valid rejection reason 10+");
        
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.REJECT, vars);
    }

    @Test
    void reject_FromN1_WithWrongRole_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        mockUserRole("ROLE_DAF"); // wrong role, should be N1

        Map<String, Object> vars = new HashMap<>();
        vars.put("rejectionReason", "Valid rejection reason 10+");
        assertThrows(WorkflowException.class, () -> 
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.REJECT, vars)
        );
    }

    @Test
    void reject_FromValide_Success() {
        invoice.setStatus(InvoiceStatus.VALIDE);
        mockUserRole("ROLE_DAF");

        Map<String, Object> vars = new HashMap<>();
        vars.put("rejectionReason", "Valid rejection reason 10+");
        
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.REJECT, vars);
    }

    @Test
    void recordPayment_Success() {
        invoice.setStatus(InvoiceStatus.BON_A_PAYER);
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.RECORD_PAYMENT, null);
    }

    @Test
    void archive_Success() {
        invoice.setStatus(InvoiceStatus.PAYE);
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ARCHIVE,
                Map.of(WorkflowExtendedStateKeys.AUTO_ARCHIVE, true));
    }

    @Test
    void resubmit_Success() {
        invoice.setStatus(InvoiceStatus.REJETE);
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.RESUBMIT, null);
    }

    @Test
    void invalidTransition_FromSoumisToValidateN1_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.SOUMIS);
        assertThrows(WorkflowException.class, () -> 
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.VALIDATE_N1, null)
        );
    }

    @Test
    void invalidTransition_FromValideToValidateN2_ThrowsWorkflowException() {
        invoice.setStatus(InvoiceStatus.VALIDE);
        assertThrows(WorkflowException.class, () -> 
            invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.VALIDATE_N2, null)
        );
    }
}
