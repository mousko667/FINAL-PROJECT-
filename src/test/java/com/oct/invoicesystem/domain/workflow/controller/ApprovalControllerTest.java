package com.oct.invoicesystem.domain.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.config.TestConfig;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;
import com.oct.invoicesystem.domain.workflow.dto.ApprovalRequest;
import com.oct.invoicesystem.domain.workflow.dto.RejectRequest;
import com.oct.invoicesystem.domain.workflow.model.RejectionReasonCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApprovalControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceDocumentRepository invoiceDocumentRepository;
    @Autowired private ApprovalStepRepository approvalStepRepository;
    @Autowired private InvoiceStatusHistoryRepository invoiceStatusHistoryRepository;
    @Autowired private InvoiceStateMachineService invoiceStateMachineService;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // --- Shared users and departments created per test class load ---
    private User assistant;
    private User n1Drh;
    private User n1Info;
    private User n2Info;
    private User daf;
    private User auditeur;
    private Department drhDept;
    private Department infoDept;

    @BeforeEach
    void setUp() {
        cleanDb();
        provisionRoles();

        drhDept  = createDepartment("DRH_T",  "Direction RH",       false, "ROLE_VALIDATEUR_N1_DRH",  null);
        infoDept = createDepartment("INFO_T",  "Informatique",       true,  "ROLE_VALIDATEUR_N1_INFO", "ROLE_VALIDATEUR_N2_INFO");

        assistant = createUser("asst_test",   "ROLE_ASSISTANT_COMPTABLE");
        n1Drh     = createUser("n1_drh_test", "ROLE_VALIDATEUR_N1_DRH");
        n1Info    = createUser("n1_info_test","ROLE_VALIDATEUR_N1_INFO");
        n2Info    = createUser("n2_info_test","ROLE_VALIDATEUR_N2_INFO");
        daf       = createUser("daf_test",    "ROLE_DAF");
        auditeur  = createUser("audit_test",  "ROLE_AUDITEUR");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        Thread.sleep(300); // Wait for any async notification listeners to finish before cleaning DB
        cleanDb();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // P3-16: single-level lifecycle – DRH (requiresN2 = false)
    // BROUILLON → SOUMIS → EN_VALIDATION_N1 → VALIDE → BON_A_PAYER → PAYE → ARCHIVE
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void p3_16_singleLevelLifecycle_DrhDepartment() throws Exception {
        Invoice invoice = createInvoice(drhDept, assistant);

        // BROUILLON → SOUMIS
        perform(post("/api/v1/invoices/{id}/submit", invoice.getId()), assistant)
                .andExpect(status().isOk());

        // AA control is now mandatory: SOUMIS → EN_CONTROLE_AA before N1 assignment.
        assignAa(invoice, assistant);

        // EN_CONTROLE_AA → EN_VALIDATION_N1. The /workflow/assign HTTP endpoint still only knows
        // the pre-AA-control states (SOUMIS/EN_VALIDATION_N2); wiring it to EN_CONTROLE_AA is
        // service/endpoint-layer work out of scope here, so drive the transition directly through
        // the state machine (same mechanism ApprovalServiceImpl.assignReviewer uses internally).
        assignReviewerViaStateMachine(invoice, n1Drh);

        // EN_VALIDATION_N1 → VALIDE  (DRH has no N2)
        perform(post("/api/v1/invoices/{id}/workflow/validate-n1", invoice.getId()),
                n1Drh, new ApprovalRequest("Looks good N1"))
                .andExpect(status().isOk());

        // VALIDE → BON_A_PAYER
        perform(post("/api/v1/invoices/{id}/workflow/bon-a-payer", invoice.getId()),
                daf, new ApprovalRequest("BAP OK"))
                .andExpect(status().isOk());

        // BON_A_PAYER → PAYE → ARCHIVE  (no HTTP endpoint yet — drive via service)
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.RECORD_PAYMENT, 
                java.util.Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId()));
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ARCHIVE, 
                java.util.Map.of(
                        WorkflowExtendedStateKeys.USER_ID, daf.getId(),
                        WorkflowExtendedStateKeys.AUTO_ARCHIVE, true));

        // Final state
        Invoice updated = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.ARCHIVE, updated.getStatus());

        // 7 transitions recorded in history (each sendEvent / HTTP call = 1 entry; the AA control
        // step SOUMIS -> EN_CONTROLE_AA adds one entry versus the pre-AA-control flow).
        long historyCount = invoiceStatusHistoryRepository.countByInvoiceId(invoice.getId());
        assertEquals(7, historyCount, "Expected 7 history entries for DRH single-level lifecycle");

        // 2 approval steps: N1 + DAF
        long stepCount = approvalStepRepository.countByInvoiceId(invoice.getId());
        assertEquals(2, stepCount, "Expected 2 approval steps: N1 and DAF");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // P3-17: two-level lifecycle – INFO (requiresN2 = true)
    // BROUILLON → SOUMIS → EN_VALIDATION_N1 → EN_VALIDATION_N2 → VALIDE → BAP → PAYE → ARCHIVE
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void p3_17_twoLevelLifecycle_InfoDepartment() throws Exception {
        Invoice invoice = createInvoice(infoDept, assistant);

        // BROUILLON → SOUMIS
        perform(post("/api/v1/invoices/{id}/submit", invoice.getId()), assistant)
                .andExpect(status().isOk());

        // AA control is now mandatory: SOUMIS → EN_CONTROLE_AA before N1 assignment.
        assignAa(invoice, assistant);

        // EN_CONTROLE_AA → EN_VALIDATION_N1  (N1 assigns self). See p3_16 comment: the
        // /workflow/assign endpoint doesn't yet know EN_CONTROLE_AA (Task 3 scope), so drive the
        // transition directly through the state machine.
        assignReviewerViaStateMachine(invoice, n1Info);

        // EN_VALIDATION_N1 → EN_VALIDATION_N2  (INFO has N2 so validate-n1 goes to N2, not VALIDE)
        perform(post("/api/v1/invoices/{id}/workflow/validate-n1", invoice.getId()),
                n1Info, new ApprovalRequest("N1 OK"))
                .andExpect(status().isOk());

        // N2 self-assigns (no state machine event, just step creation)
        perform(post("/api/v1/invoices/{id}/workflow/assign", invoice.getId()), n2Info)
                .andExpect(status().isOk());

        // EN_VALIDATION_N2 → VALIDE
        perform(post("/api/v1/invoices/{id}/workflow/validate-n2", invoice.getId()),
                n2Info, new ApprovalRequest("N2 OK"))
                .andExpect(status().isOk());

        // VALIDE → BON_A_PAYER
        perform(post("/api/v1/invoices/{id}/workflow/bon-a-payer", invoice.getId()),
                daf, new ApprovalRequest("DAF OK"))
                .andExpect(status().isOk());

        // BON_A_PAYER → PAYE → ARCHIVE
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.RECORD_PAYMENT, 
                java.util.Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId()));
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ARCHIVE, 
                java.util.Map.of(
                        WorkflowExtendedStateKeys.USER_ID, daf.getId(),
                        WorkflowExtendedStateKeys.AUTO_ARCHIVE, true));

        Invoice updated = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.ARCHIVE, updated.getStatus());

        // 3 approval steps: N1 + N2 + DAF
        long stepCount = approvalStepRepository.countByInvoiceId(invoice.getId());
        assertEquals(3, stepCount, "Expected 3 approval steps: N1, N2, and DAF");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // P3-18: reject + resubmit flow
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void p3_18_rejectAndResubmit() throws Exception {
        Invoice invoice = createInvoice(drhDept, assistant);

        // Submit
        perform(post("/api/v1/invoices/{id}/submit", invoice.getId()), assistant)
                .andExpect(status().isOk());

        // AA control is now mandatory: SOUMIS → EN_CONTROLE_AA before N1 assignment.
        assignAa(invoice, assistant);

        // Assign N1 (see p3_16 comment: drive directly through the state machine since
        // /workflow/assign doesn't yet know EN_CONTROLE_AA)
        assignReviewerViaStateMachine(invoice, n1Drh);

        // Reject with predefined reason code + detail
        RejectRequest rejectRequest = RejectRequest.builder()
                .reasonCode(RejectionReasonCode.PIECE_MANQUANTE)
                .rejectionReason("document justificatif absent")
                .build();
        perform(post("/api/v1/invoices/{id}/workflow/reject", invoice.getId()),
                n1Drh, rejectRequest)
                .andExpect(status().isOk());

        // Assert REJETE status
        Invoice afterReject = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.REJETE, afterReject.getStatus());

        // Resubmit → SOUMIS
        perform(post("/api/v1/invoices/{id}/resubmit", invoice.getId()), assistant)
                .andExpect(status().isOk());

        Invoice afterResubmit = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.SOUMIS, afterResubmit.getStatus());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // P3-19: wrong-role → 403 on every workflow action
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void p3_19_wrongRole_Returns403ForAllWorkflowActions() throws Exception {
        Invoice invoice = createInvoice(drhDept, assistant);

        // Put invoice in SOUMIS so assign is callable
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.SUBMIT,
                java.util.Map.of(WorkflowExtendedStateKeys.USER_ID, assistant.getId()));

        // AA control is now mandatory: SOUMIS → EN_CONTROLE_AA before N1 assignment.
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ASSIGN_AA,
                java.util.Map.of(WorkflowExtendedStateKeys.USER_ID, assistant.getId()));

        // AUDITEUR trying to assign reviewer → 403 (needs ROLE_VALIDATEUR_N1_DRH)
        perform(post("/api/v1/invoices/{id}/workflow/assign", invoice.getId()), auditeur)
                .andExpect(status().isForbidden());

        // Advance to EN_VALIDATION_N1 legitimately
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ASSIGN_REVIEWER,
                java.util.Map.of(
                    WorkflowExtendedStateKeys.USER_ID, n1Drh.getId(),
                    "userId", n1Drh.getId(), 
                    "department", drhDept.getCode()
                ));

        // ASSISTANT trying to validate-n1 → 403 (needs ROLE_VALIDATEUR_N1_DRH)
        perform(post("/api/v1/invoices/{id}/workflow/validate-n1", invoice.getId()),
                assistant, new ApprovalRequest("sneaky"))
                .andExpect(status().isForbidden());

        // N1 trying to do validate-n2 → 403: the @PreAuthorize role check (validate-n2 requires a
        // ROLE_VALIDATEUR_N2_* role) rejects before the workflow logic runs, so authorization (403)
        // takes precedence over any WorkflowException (400). This is the correct security ordering.
        perform(post("/api/v1/invoices/{id}/workflow/validate-n2", invoice.getId()),
                n1Drh, new ApprovalRequest("N1 usurping N2"))
                .andExpect(status().isForbidden());

        // Advance to VALIDE legitimately
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.VALIDATE_N1,
                java.util.Map.of(
                    WorkflowExtendedStateKeys.USER_ID, n1Drh.getId(),
                    "comment", "ok"
                ));

        // N1 trying to do BON_A_PAYER → 403 (needs ROLE_DAF)
        perform(post("/api/v1/invoices/{id}/workflow/bon-a-payer", invoice.getId()),
                n1Drh, new ApprovalRequest("N1 pretending to be DAF"))
                .andExpect(status().isForbidden());

        // AUDITEUR trying to reject → 403 (wrong role)
        perform(post("/api/v1/invoices/{id}/workflow/reject", invoice.getId()),
                auditeur, RejectRequest.builder()
                        .reasonCode(RejectionReasonCode.AUTRE)
                        .rejectionReason("I want to reject this invoice")
                        .build())
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // C1: predefined rejection reasons (M4 #8)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void rejectionReasons_returnsTranslatedOptions_fr() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/{id}/workflow/rejection-reasons", UUID.randomUUID())
                        .header("Accept-Language", "fr")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(daf, null, daf.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[?(@.code=='AUTRE')].label")
                        .value(org.hamcrest.Matchers.hasItem("Autre")));
    }

    @Test
    void reject_withCodeAndDetail_persistsBracketedReason() throws Exception {
        Invoice invoice = submitAndAssignN1Drh();

        RejectRequest req = RejectRequest.builder()
                .reasonCode(RejectionReasonCode.MONTANT_INCORRECT)
                .rejectionReason("le HT ne correspond pas au BDC")
                .build();
        perform(post("/api/v1/invoices/{id}/workflow/reject", invoice.getId()), n1Drh, req)
                .andExpect(status().isOk());

        String stored = approvalStepRepository.findByInvoiceIdOrderByStepOrderAsc(invoice.getId()).stream()
                .map(s -> s.getRejectionReason())
                .filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow();
        assertEquals("[MONTANT_INCORRECT] le HT ne correspond pas au BDC", stored);
    }

    @Test
    void reject_withPredefinedCodeAndNoDetail_succeeds() throws Exception {
        // N2 (PROB-117): a predefined code is a structured reason on its own; the 10-char minimum
        // must NOT apply to the composed "[CODE]" string. "[DOUBLON]" is 9 chars and used to fail.
        Invoice invoice = submitAndAssignN1Drh();

        RejectRequest req = RejectRequest.builder()
                .reasonCode(RejectionReasonCode.DOUBLON)
                .rejectionReason(null)
                .build();
        perform(post("/api/v1/invoices/{id}/workflow/reject", invoice.getId()), n1Drh, req)
                .andExpect(status().isOk());

        String stored = approvalStepRepository.findByInvoiceIdOrderByStepOrderAsc(invoice.getId()).stream()
                .map(s -> s.getRejectionReason())
                .filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow();
        assertEquals("[DOUBLON]", stored);
    }

    @Test
    void reject_withOtherCodeAndNoDetail_returns400() throws Exception {
        Invoice invoice = submitAndAssignN1Drh();

        RejectRequest req = RejectRequest.builder()
                .reasonCode(RejectionReasonCode.AUTRE)
                .rejectionReason(null)
                .build();
        perform(post("/api/v1/invoices/{id}/workflow/reject", invoice.getId()), n1Drh, req)
                .andExpect(status().isBadRequest());
    }

    @Test
    void reject_withNullCode_returns400() throws Exception {
        Invoice invoice = submitAndAssignN1Drh();

        RejectRequest req = RejectRequest.builder()
                .reasonCode(null)
                .rejectionReason("un détail parfaitement valide ici")
                .build();
        perform(post("/api/v1/invoices/{id}/workflow/reject", invoice.getId()), n1Drh, req)
                .andExpect(status().isBadRequest());
    }

    /**
     * Brings a fresh DRH invoice to EN_VALIDATION_N1 (submit + AA control + N1 self-assign),
     * ready to reject.
     */
    private Invoice submitAndAssignN1Drh() throws Exception {
        Invoice invoice = createInvoice(drhDept, assistant);
        perform(post("/api/v1/invoices/{id}/submit", invoice.getId()), assistant)
                .andExpect(status().isOk());
        assignAa(invoice, assistant);
        assignReviewerViaStateMachine(invoice, n1Drh);
        return invoice;
    }

    /**
     * Drives the mandatory AA control step (SOUMIS → EN_CONTROLE_AA) directly through the state
     * machine service, since the assistant-comptable HTTP endpoint for ASSIGN_AA is out of scope
     * for this task (introduced separately at the service/endpoint layer).
     */
    private void assignAa(Invoice invoice, User assistantUser) {
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ASSIGN_AA,
                java.util.Map.of(WorkflowExtendedStateKeys.USER_ID, assistantUser.getId()));
    }

    /**
     * Drives EN_CONTROLE_AA → EN_VALIDATION_N1 directly through the state machine service. The
     * /workflow/assign HTTP endpoint (ApprovalServiceImpl.assignReviewer) does not yet recognize
     * EN_CONTROLE_AA as a valid source state — wiring it up is service/endpoint-layer work
     * reserved for a later task — so tests exercise the transition the same way
     * p3_19_wrongRole_Returns403ForAllWorkflowActions already does for ASSIGN_REVIEWER.
     */
    private void assignReviewerViaStateMachine(Invoice invoice, User n1Approver) {
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.ASSIGN_REVIEWER,
                java.util.Map.of(WorkflowExtendedStateKeys.USER_ID, n1Approver.getId()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            User user) throws Exception {
        return mockMvc.perform(builder
                .with(SecurityMockMvcRequestPostProcessors.authentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()))));
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            User user,
            Object body) throws Exception {
        return mockMvc.perform(builder
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .with(SecurityMockMvcRequestPostProcessors.authentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()))));
    }

    private void cleanDb() {
        jdbcTemplate.execute("DELETE FROM notifications");
        approvalStepRepository.deleteAll();
        invoiceStatusHistoryRepository.deleteAll();
        invoiceDocumentRepository.deleteAll();
        invoiceRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        departmentRepository.deleteAll();
    }

    private void provisionRoles() {
        for (String name : new String[]{
                "ROLE_ASSISTANT_COMPTABLE",
                "ROLE_VALIDATEUR_N1_DRH",
                "ROLE_VALIDATEUR_N1_INFO",
                "ROLE_VALIDATEUR_N2_INFO",
                "ROLE_DAF",
                "ROLE_AUDITEUR"
        }) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(Role.builder().name(name).description(name).build());
            }
        }
    }

    private Department createDepartment(String code, String nameFr, boolean requiresN2,
                                        String n1Role, String n2Role) {
        Department dept = new Department();
        dept.setCode(code);
        dept.setNameFr(nameFr);
        dept.setNameEn(nameFr);
        dept.setRequiresN2(requiresN2);
        dept.setN1Role(n1Role);
        dept.setN2Role(n2Role);
        return departmentRepository.save(dept);
    }

    private User createUser(String username, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow(
                () -> new IllegalStateException("Role not found: " + roleName));

        // Step 1: persist user WITHOUT roles so the UUID is assigned
        User user = User.builder()
                .username(username)
                .email(username + "@oct.test")
                .password("$2a$12$hashedpassword")
                .firstName(username)
                .lastName("Test")
                .preferredLang("fr")
                .build();
        
        // Mark every test user as MFA-verified so the MfaSetupEnforcementFilter never blocks
        // requests in these workflow tests. The filter treats ASSISTANT_COMPTABLE, DAF, ADMIN and
        // all VALIDATEUR_* roles as mandatory-MFA; setting it unconditionally keeps the helper
        // correct even if the prod role list changes (test profile: enforce-secret-check=false).
        user.setMfaEnabled(true);
        user.setMfaVerified(true);

        user = userRepository.save(user);   // user.getId() is now non-null

        // Step 2: build UserRole with explicit composite key and add it
        UserRole ur = UserRole.builder()
                .id(new UserRoleId(user.getId(), role.getId()))
                .user(user)
                .role(role)
                .build();
        user.getUserRoles().add(ur);
        user = userRepository.save(user);   // cascade persists the UserRole

        // Re-fetch fully loaded (with roles eager via @EntityGraph on findByUsername)
        return userRepository.findByUsername(user.getUsername()).orElseThrow();
    }

    private Invoice createInvoice(Department dept, User actor) {
        Invoice invoice = Invoice.builder()
                .referenceNumber("FAC-2026-" + UUID.randomUUID().toString().substring(0, 8))
                .department(dept)
                .submittedBy(actor)
                .supplierName("ACME Corp")
                .supplierEmail("acme@corp.test")
                .amount(new BigDecimal("5000.00"))
                .currency("XAF")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .status(InvoiceStatus.BROUILLON)
                .version(1)
                .build();
        invoice = invoiceRepository.save(invoice);

        // DocumentRequiredGuard: invoice must have ≥1 document to transition from BROUILLON
        InvoiceDocument doc = InvoiceDocument.builder()
                .invoice(invoice)
                .originalFilename("facture.pdf")
                .fileType("application/pdf")
                .fileSizeBytes(1024L)
                .minioObjectKey("inv/" + invoice.getId().toString().substring(0, 8))
                .checksumSha256("a".repeat(64))
                .uploadedBy(actor)
                .build();
        invoiceDocumentRepository.save(doc);

        return invoice;
    }
}
