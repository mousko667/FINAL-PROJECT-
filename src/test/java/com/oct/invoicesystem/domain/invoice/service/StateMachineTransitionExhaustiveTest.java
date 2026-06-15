package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.config.TestConfig;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive test of all valid and invalid state machine transitions for the invoice BAP workflow.
 *
 * Valid transitions tested:
 *   T1  BROUILLON       → SOUMIS           (SUBMIT)
 *   T2  SOUMIS          → EN_VALIDATION_N1 (ASSIGN_REVIEWER)
 *   T3  EN_VALIDATION_N1→ EN_VALIDATION_N2 (VALIDATE_N1, two-level dept)
 *   T4  EN_VALIDATION_N1→ VALIDE           (VALIDATE_N1, single-level dept)
 *   T5  EN_VALIDATION_N2→ VALIDE           (VALIDATE_N2)
 *   T6  VALIDE          → BON_A_PAYER      (BON_A_PAYER)
 *   T7  BON_A_PAYER     → PAYE             (RECORD_PAYMENT)
 *   T8  PAYE            → ARCHIVE          (ARCHIVE with AUTO_ARCHIVE=true)
 *   T9  EN_VALIDATION_N1→ REJETE           (REJECT)
 *   T10 EN_VALIDATION_N2→ REJETE           (REJECT)
 *   T11 VALIDE          → REJETE           (REJECT)
 *   T12 REJETE          → SOUMIS           (RESUBMIT)
 *
 * Invalid transitions tested:
 *   I1  BROUILLON → ARCHIVE   (direct archive blocked — not AUTO_ARCHIVE)
 *   I2  SOUMIS    → VALIDE    (must go through N1)
 *   I3  BROUILLON → REJETE    (cannot reject a draft)
 *   I4  ARCHIVE   → SOUMIS    (terminal state, no way back)
 *   I5  PAYE      → REJETE    (cannot reject a paid invoice)
 */
@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@DisplayName("StateMachine — Exhaustive Transition Tests")
class StateMachineTransitionExhaustiveTest {

    @Autowired private InvoiceStateMachineService stateMachineService;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceDocumentRepository documentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ApprovalStepRepository approvalStepRepository;
    @Autowired private InvoiceStatusHistoryRepository historyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User assistant;
    private User n1Drh;
    private User n1Info;
    private User n2Info;
    private User daf;
    private Department drhDept;   // single-level
    private Department infoDept;  // two-level

    @BeforeEach
    void setUp() {
        cleanDb();
        provisionRoles();
        drhDept  = dept("EXHAUST_DRH",  false, "ROLE_VALIDATEUR_N1_DRH",  null);
        infoDept = dept("EXHAUST_INFO", true,  "ROLE_VALIDATEUR_N1_INFO", "ROLE_VALIDATEUR_N2_INFO");
        assistant = user("ex_asst",   "ROLE_ASSISTANT_COMPTABLE");
        n1Drh     = user("ex_n1drh",  "ROLE_VALIDATEUR_N1_DRH");
        n1Info    = user("ex_n1info", "ROLE_VALIDATEUR_N1_INFO");
        n2Info    = user("ex_n2info", "ROLE_VALIDATEUR_N2_INFO");
        daf       = user("ex_daf",    "ROLE_DAF");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        Thread.sleep(200);
        cleanDb();
        SecurityContextHolder.clearContext();
    }

    // ── Valid transition T1: BROUILLON → SOUMIS ──────────────────────────────

    @Test
    @DisplayName("T1 BROUILLON → SOUMIS via SUBMIT")
    void t1_brouillon_to_soumis() {
        Invoice inv = invoice(drhDept);
        auth(assistant);
        sendEvent(inv.getId(), InvoiceEvent.SUBMIT, Map.of(WorkflowExtendedStateKeys.USER_ID, assistant.getId()));
        assertEquals(InvoiceStatus.SOUMIS, reload(inv).getStatus());
    }

    // ── Valid transition T2: SOUMIS → EN_VALIDATION_N1 ───────────────────────

    @Test
    @DisplayName("T2 SOUMIS → EN_VALIDATION_N1 via ASSIGN_REVIEWER")
    void t2_soumis_to_en_validation_n1() {
        Invoice inv = advanceTo(InvoiceStatus.SOUMIS, drhDept, assistant, n1Drh);
        auth(n1Drh);
        sendEvent(inv.getId(), InvoiceEvent.ASSIGN_REVIEWER, Map.of(WorkflowExtendedStateKeys.USER_ID, n1Drh.getId()));
        assertEquals(InvoiceStatus.EN_VALIDATION_N1, reload(inv).getStatus());
    }

    // ── Valid transition T3: EN_VALIDATION_N1 → EN_VALIDATION_N2 (two-level) ─

    @Test
    @DisplayName("T3 EN_VALIDATION_N1 → EN_VALIDATION_N2 via VALIDATE_N1 (INFO dept)")
    void t3_en_validation_n1_to_en_validation_n2() {
        Invoice inv = advanceTo(InvoiceStatus.EN_VALIDATION_N1, infoDept, assistant, n1Info);
        auth(n1Info);
        sendEvent(inv.getId(), InvoiceEvent.VALIDATE_N1, Map.of(WorkflowExtendedStateKeys.USER_ID, n1Info.getId()));
        assertEquals(InvoiceStatus.EN_VALIDATION_N2, reload(inv).getStatus());
    }

    // ── Valid transition T4: EN_VALIDATION_N1 → VALIDE (single-level) ────────

    @Test
    @DisplayName("T4 EN_VALIDATION_N1 → VALIDE via VALIDATE_N1 (DRH dept, single-level)")
    void t4_en_validation_n1_to_valide_single_level() {
        Invoice inv = advanceTo(InvoiceStatus.EN_VALIDATION_N1, drhDept, assistant, n1Drh);
        auth(n1Drh);
        sendEvent(inv.getId(), InvoiceEvent.VALIDATE_N1, Map.of(WorkflowExtendedStateKeys.USER_ID, n1Drh.getId()));
        assertEquals(InvoiceStatus.VALIDE, reload(inv).getStatus());
    }

    // ── Valid transition T5: EN_VALIDATION_N2 → VALIDE ───────────────────────

    @Test
    @DisplayName("T5 EN_VALIDATION_N2 → VALIDE via VALIDATE_N2")
    void t5_en_validation_n2_to_valide() {
        Invoice inv = advanceTo(InvoiceStatus.EN_VALIDATION_N2, infoDept, assistant, n2Info);
        auth(n2Info);
        sendEvent(inv.getId(), InvoiceEvent.VALIDATE_N2, Map.of(WorkflowExtendedStateKeys.USER_ID, n2Info.getId()));
        assertEquals(InvoiceStatus.VALIDE, reload(inv).getStatus());
    }

    // ── Valid transition T6: VALIDE → BON_A_PAYER ────────────────────────────

    @Test
    @DisplayName("T6 VALIDE → BON_A_PAYER via BON_A_PAYER event")
    void t6_valide_to_bon_a_payer() {
        Invoice inv = advanceTo(InvoiceStatus.VALIDE, drhDept, assistant, n1Drh);
        auth(daf);
        sendEvent(inv.getId(), InvoiceEvent.BON_A_PAYER, Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId()));
        assertEquals(InvoiceStatus.BON_A_PAYER, reload(inv).getStatus());
    }

    // ── Valid transition T7: BON_A_PAYER → PAYE ──────────────────────────────

    @Test
    @DisplayName("T7 BON_A_PAYER → PAYE via RECORD_PAYMENT")
    void t7_bon_a_payer_to_paye() {
        Invoice inv = advanceTo(InvoiceStatus.BON_A_PAYER, drhDept, assistant, n1Drh);
        auth(daf);
        sendEvent(inv.getId(), InvoiceEvent.RECORD_PAYMENT, Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId()));
        assertEquals(InvoiceStatus.PAYE, reload(inv).getStatus());
    }

    // ── Valid transition T8: PAYE → ARCHIVE ──────────────────────────────────

    @Test
    @DisplayName("T8 PAYE → ARCHIVE via ARCHIVE (AUTO_ARCHIVE=true)")
    void t8_paye_to_archive() {
        Invoice inv = advanceTo(InvoiceStatus.PAYE, drhDept, assistant, n1Drh);
        auth(daf);
        sendEvent(inv.getId(), InvoiceEvent.ARCHIVE,
                Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId(), WorkflowExtendedStateKeys.AUTO_ARCHIVE, true));
        assertEquals(InvoiceStatus.ARCHIVE, reload(inv).getStatus());
    }

    // ── Valid transition T9: EN_VALIDATION_N1 → REJETE ───────────────────────

    @Test
    @DisplayName("T9 EN_VALIDATION_N1 → REJETE via REJECT")
    void t9_en_validation_n1_to_rejete() {
        Invoice inv = advanceTo(InvoiceStatus.EN_VALIDATION_N1, drhDept, assistant, n1Drh);
        auth(n1Drh);
        sendEvent(inv.getId(), InvoiceEvent.REJECT,
                Map.of(WorkflowExtendedStateKeys.USER_ID, n1Drh.getId(), "rejectionReason", "Missing document proof"));
        assertEquals(InvoiceStatus.REJETE, reload(inv).getStatus());
    }

    // ── Valid transition T10: EN_VALIDATION_N2 → REJETE ──────────────────────

    @Test
    @DisplayName("T10 EN_VALIDATION_N2 → REJETE via REJECT")
    void t10_en_validation_n2_to_rejete() {
        Invoice inv = advanceTo(InvoiceStatus.EN_VALIDATION_N2, infoDept, assistant, n2Info);
        auth(n2Info);
        sendEvent(inv.getId(), InvoiceEvent.REJECT,
                Map.of(WorkflowExtendedStateKeys.USER_ID, n2Info.getId(), "rejectionReason", "Amount discrepancy found"));
        assertEquals(InvoiceStatus.REJETE, reload(inv).getStatus());
    }

    // ── Valid transition T11: VALIDE → REJETE ────────────────────────────────

    @Test
    @DisplayName("T11 VALIDE → REJETE via REJECT (DAF role)")
    void t11_valide_to_rejete() {
        Invoice inv = advanceTo(InvoiceStatus.VALIDE, drhDept, assistant, n1Drh);
        auth(daf);
        sendEvent(inv.getId(), InvoiceEvent.REJECT,
                Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId(), "rejectionReason", "Budget not available this quarter"));
        assertEquals(InvoiceStatus.REJETE, reload(inv).getStatus());
    }

    // ── Valid transition T12: REJETE → SOUMIS ────────────────────────────────

    @Test
    @DisplayName("T12 REJETE → SOUMIS via RESUBMIT")
    void t12_rejete_to_soumis() {
        Invoice inv = advanceTo(InvoiceStatus.REJETE, drhDept, assistant, n1Drh);
        auth(assistant);
        sendEvent(inv.getId(), InvoiceEvent.RESUBMIT, Map.of(WorkflowExtendedStateKeys.USER_ID, assistant.getId()));
        assertEquals(InvoiceStatus.SOUMIS, reload(inv).getStatus());
    }

    // ── Invalid transition I1: BROUILLON → ARCHIVE (manual archive blocked) ──

    @Test
    @DisplayName("I1 BROUILLON → ARCHIVE blocked (auto-archive only)")
    void i1_brouillon_to_archive_blocked() {
        Invoice inv = invoice(drhDept);
        auth(daf);
        assertThrows(WorkflowException.class, () ->
                sendEvent(inv.getId(), InvoiceEvent.ARCHIVE,
                        Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId())),
                "Manual ARCHIVE must be rejected with WorkflowException");
    }

    // ── Invalid transition I2: SOUMIS → VALIDE (skipping N1) ─────────────────

    @Test
    @DisplayName("I2 SOUMIS → VALIDE blocked (must go through N1 assignment)")
    void i2_soumis_to_valide_blocked() {
        Invoice inv = advanceTo(InvoiceStatus.SOUMIS, drhDept, assistant, n1Drh);
        auth(n1Drh);
        assertThrows(WorkflowException.class, () ->
                sendEvent(inv.getId(), InvoiceEvent.VALIDATE_N1,
                        Map.of(WorkflowExtendedStateKeys.USER_ID, n1Drh.getId())),
                "VALIDATE_N1 from SOUMIS must be rejected");
    }

    // ── Invalid transition I3: BROUILLON → REJETE ────────────────────────────

    @Test
    @DisplayName("I3 BROUILLON → REJETE blocked (cannot reject a draft)")
    void i3_brouillon_to_rejete_blocked() {
        Invoice inv = invoice(drhDept);
        auth(n1Drh);
        assertThrows(WorkflowException.class, () ->
                sendEvent(inv.getId(), InvoiceEvent.REJECT,
                        Map.of(WorkflowExtendedStateKeys.USER_ID, n1Drh.getId(), "rejectionReason", "Should not work at all")),
                "REJECT from BROUILLON must be rejected");
    }

    // ── Invalid transition I4: ARCHIVE → SOUMIS (terminal state) ─────────────

    @Test
    @DisplayName("I4 ARCHIVE → SOUMIS blocked (terminal state, no transitions out)")
    void i4_archive_to_soumis_blocked() {
        Invoice inv = advanceTo(InvoiceStatus.ARCHIVE, drhDept, assistant, n1Drh);
        auth(assistant);
        assertThrows(WorkflowException.class, () ->
                sendEvent(inv.getId(), InvoiceEvent.RESUBMIT,
                        Map.of(WorkflowExtendedStateKeys.USER_ID, assistant.getId())),
                "RESUBMIT from ARCHIVE must be rejected");
    }

    // ── Invalid transition I5: PAYE → REJETE ─────────────────────────────────

    @Test
    @DisplayName("I5 PAYE → REJETE blocked (cannot reject an already-paid invoice)")
    void i5_paye_to_rejete_blocked() {
        Invoice inv = advanceTo(InvoiceStatus.PAYE, drhDept, assistant, n1Drh);
        auth(daf);
        assertThrows(WorkflowException.class, () ->
                sendEvent(inv.getId(), InvoiceEvent.REJECT,
                        Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId(), "rejectionReason", "Too late to reject this")),
                "REJECT from PAYE must be rejected");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendEvent(UUID invoiceId, InvoiceEvent event, Map<String, Object> vars) {
        stateMachineService.sendEvent(invoiceId, event, vars);
    }

    private Invoice reload(Invoice inv) {
        return invoiceRepository.findById(inv.getId()).orElseThrow();
    }

    private void auth(User u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    /**
     * Advance an invoice to the desired status using the state machine (not direct DB writes),
     * so history and steps are properly recorded.
     */
    private Invoice advanceTo(InvoiceStatus target, Department d, User submitter, User approver) {
        Invoice inv = invoice(d);
        UUID sid = submitter.getId();

        if (target == InvoiceStatus.BROUILLON) return inv;

        auth(submitter);
        sendEvent(inv.getId(), InvoiceEvent.SUBMIT, Map.of(WorkflowExtendedStateKeys.USER_ID, sid));
        if (target == InvoiceStatus.SOUMIS) return inv;

        // For a two-level dept the N1 steps (ASSIGN_REVIEWER + VALIDATE_N1) must be performed by
        // the N1 validator, regardless of which approver the caller ultimately targets (callers
        // for EN_VALIDATION_N2 / VALIDE / REJETE pass n2Info as `approver`). Use n1Info for N1.
        boolean twoLevel = d.isRequiresN2();
        User n1Actor = twoLevel ? n1Info : approver;
        UUID n1Aid = n1Actor.getId();

        auth(n1Actor);
        sendEvent(inv.getId(), InvoiceEvent.ASSIGN_REVIEWER, Map.of(WorkflowExtendedStateKeys.USER_ID, n1Aid));
        if (target == InvoiceStatus.EN_VALIDATION_N1) return inv;

        if (twoLevel && target == InvoiceStatus.EN_VALIDATION_N2) {
            sendEvent(inv.getId(), InvoiceEvent.VALIDATE_N1, Map.of(WorkflowExtendedStateKeys.USER_ID, n1Aid));
            return inv;
        }

        // For REJETE target, stop at the deepest validation state and let the test reject
        if (target == InvoiceStatus.REJETE) {
            if (twoLevel) {
                sendEvent(inv.getId(), InvoiceEvent.VALIDATE_N1, Map.of(WorkflowExtendedStateKeys.USER_ID, n1Aid));
                // advance into N2 so the test can reject from EN_VALIDATION_N2
                auth(n2Info);
                sendEvent(inv.getId(), InvoiceEvent.ASSIGN_REVIEWER, Map.of(WorkflowExtendedStateKeys.USER_ID, n2Info.getId()));
                sendEvent(inv.getId(), InvoiceEvent.REJECT,
                        Map.of(WorkflowExtendedStateKeys.USER_ID, n2Info.getId(), "rejectionReason", "Auto-reject for test setup"));
            } else {
                sendEvent(inv.getId(), InvoiceEvent.REJECT,
                        Map.of(WorkflowExtendedStateKeys.USER_ID, n1Aid, "rejectionReason", "Auto-reject for test setup"));
            }
            return inv;
        }

        sendEvent(inv.getId(), InvoiceEvent.VALIDATE_N1, Map.of(WorkflowExtendedStateKeys.USER_ID, n1Aid));
        if (twoLevel) {
            auth(n2Info);
            sendEvent(inv.getId(), InvoiceEvent.ASSIGN_REVIEWER, Map.of(WorkflowExtendedStateKeys.USER_ID, n2Info.getId()));
            sendEvent(inv.getId(), InvoiceEvent.VALIDATE_N2, Map.of(WorkflowExtendedStateKeys.USER_ID, n2Info.getId()));
        }
        if (target == InvoiceStatus.VALIDE) return inv;

        auth(daf);
        sendEvent(inv.getId(), InvoiceEvent.BON_A_PAYER, Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId()));
        if (target == InvoiceStatus.BON_A_PAYER) return inv;

        sendEvent(inv.getId(), InvoiceEvent.RECORD_PAYMENT, Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId()));
        if (target == InvoiceStatus.PAYE) return inv;

        sendEvent(inv.getId(), InvoiceEvent.ARCHIVE,
                Map.of(WorkflowExtendedStateKeys.USER_ID, daf.getId(), WorkflowExtendedStateKeys.AUTO_ARCHIVE, true));
        return inv;
    }

    private Invoice invoice(Department d) {
        Invoice inv = Invoice.builder()
                .referenceNumber("FAC-2026-EX" + UUID.randomUUID().toString().substring(0, 6))
                .department(d)
                .submittedBy(assistant)
                .supplierName("Test Supplier")
                .supplierEmail("supplier@test.com")
                .amount(new BigDecimal("1000.00"))
                .currency("XAF")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .status(InvoiceStatus.BROUILLON)
                .version(1)
                .build();
        inv = invoiceRepository.save(inv);

        InvoiceDocument doc = InvoiceDocument.builder()
                .invoice(inv)
                .originalFilename("test.pdf")
                .fileType("application/pdf")
                .fileSizeBytes(512L)
                .minioObjectKey("test/" + inv.getId())
                .checksumSha256("a".repeat(64))
                .uploadedBy(assistant)
                .build();
        documentRepository.save(doc);
        return inv;
    }

    private Department dept(String code, boolean requiresN2, String n1Role, String n2Role) {
        Department d = new Department();
        d.setCode(code);
        d.setNameFr(code);
        d.setNameEn(code);
        d.setRequiresN2(requiresN2);
        d.setN1Role(n1Role);
        d.setN2Role(n2Role);
        return departmentRepository.save(d);
    }

    private User user(String username, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).description(roleName).build()));

        User u = User.builder()
                .username(username)
                .email(username + "@oct.test")
                .password("$2a$12$hashed")
                .firstName(username)
                .lastName("Test")
                .preferredLang("fr")
                .build();
        boolean needsMfa = "ROLE_DAF".equals(roleName) || "ROLE_ADMIN".equals(roleName)
                || roleName.startsWith("ROLE_VALIDATEUR_N1_") || roleName.startsWith("ROLE_VALIDATEUR_N2_");
        if (needsMfa) {
            u.setMfaEnabled(true);
            u.setMfaVerified(true);
        }
        u = userRepository.save(u);

        UserRole ur = UserRole.builder()
                .id(new UserRoleId(u.getId(), role.getId()))
                .user(u)
                .role(role)
                .build();
        u.getUserRoles().add(ur);
        u = userRepository.save(u);
        return userRepository.findByUsername(u.getUsername()).orElseThrow();
    }

    private void provisionRoles() {
        for (String name : new String[]{
                "ROLE_ASSISTANT_COMPTABLE", "ROLE_DAF",
                "ROLE_VALIDATEUR_N1_DRH", "ROLE_VALIDATEUR_N1_INFO",
                "ROLE_VALIDATEUR_N2_INFO"}) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(Role.builder().name(name).description(name).build());
            }
        }
    }

    private void cleanDb() {
        approvalStepRepository.deleteAll();
        historyRepository.deleteAll();
        documentRepository.deleteAll();
        // Async (@Async) notification events from rejection/approval transitions can land slightly
        // after the transition returns. Delete notifications LAST — right before invoices — so any
        // late insert is cleared and the notifications→invoices FK can't be violated on teardown.
        jdbcTemplate.execute("DELETE FROM notifications");
        invoiceRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        departmentRepository.deleteAll();
    }
}
