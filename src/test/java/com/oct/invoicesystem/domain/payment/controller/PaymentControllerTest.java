package com.oct.invoicesystem.domain.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.payment.dto.PaymentRequest;
import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.HttpHeaders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private MinioStorageService minioStorageService;

    private User assistant;
    private User auditeur;
    private User daf;
    private User admin;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        Department dept = new Department();
        dept.setCode("IT");
        dept.setNameFr("Information Technology");
        dept.setNameEn("Information Technology");
        dept.setN1Role("ROLE_MANAGER");
        dept.setActive(true);
        dept.setRequiresN2(false);
        departmentRepository.save(dept);

        Role roleAssistant = roleRepository.findByName("ROLE_ASSISTANT_COMPTABLE").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_ASSISTANT_COMPTABLE");
            return roleRepository.save(r);
        });

        Role roleAuditeur = roleRepository.findByName("ROLE_AUDITEUR").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_AUDITEUR");
            return roleRepository.save(r);
        });

        assistant = new User();
        assistant.setUsername("assistant");
        assistant.setEmail("assistant@mail.com");
        assistant.setPassword(passwordEncoder.encode("Password123!"));
        assistant.setFirstName("Assis");
        assistant.setLastName("Tant");
        // ASSISTANT_COMPTABLE is a mandatory-MFA role; mark verified so the
        // MfaSetupEnforcementFilter does not block these payment requests
        // (test profile: enforce-secret-check=false, so no TOTP secret needed).
        assistant.setMfaEnabled(true);
        assistant.setMfaVerified(true);
        assistant = userRepository.save(assistant);

        UserRole urAsst = UserRole.builder()
                .id(new UserRoleId(assistant.getId(), roleAssistant.getId()))
                .user(assistant)
                .role(roleAssistant)
                .build();
        assistant.getUserRoles().add(urAsst);
        assistant = userRepository.save(assistant);

        auditeur = new User();
        auditeur.setUsername("auditeur");
        auditeur.setEmail("auditeur@mail.com");
        auditeur.setPassword(passwordEncoder.encode("Password123!"));
        auditeur.setFirstName("Aud");
        auditeur.setLastName("Iteur");
        // MFA is mandatory for all staff roles (incl. AUDITEUR); mark verified so this test
        // isolates the authorization (403) check rather than tripping the MFA-setup gate.
        auditeur.setMfaEnabled(true);
        auditeur.setMfaVerified(true);
        auditeur = userRepository.save(auditeur);

        UserRole urAud = UserRole.builder()
                .id(new UserRoleId(auditeur.getId(), roleAuditeur.getId()))
                .user(auditeur)
                .role(roleAuditeur)
                .build();
        auditeur.getUserRoles().add(urAud);
        auditeur = userRepository.save(auditeur);

        // PROB-065 (SoD): DAF must retain read access to payments; ADMIN must not.
        Role roleDaf = roleRepository.findByName("ROLE_DAF").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_DAF");
            return roleRepository.save(r);
        });

        daf = new User();
        daf.setUsername("daf_pay_test");
        daf.setEmail("daf_pay_test@mail.com");
        daf.setPassword(passwordEncoder.encode("Password123!"));
        daf.setFirstName("Daf");
        daf.setLastName("Test");
        daf.setMfaEnabled(true);
        daf.setMfaVerified(true);
        daf = userRepository.save(daf);

        UserRole urDaf = UserRole.builder()
                .id(new UserRoleId(daf.getId(), roleDaf.getId()))
                .user(daf)
                .role(roleDaf)
                .build();
        daf.getUserRoles().add(urDaf);
        daf = userRepository.save(daf);

        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_ADMIN");
            return roleRepository.save(r);
        });

        admin = new User();
        admin.setUsername("admin_pay_test");
        admin.setEmail("admin_pay_test@mail.com");
        admin.setPassword(passwordEncoder.encode("Password123!"));
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setMfaEnabled(true);
        admin.setMfaVerified(true);
        admin = userRepository.save(admin);

        UserRole urAdmin = UserRole.builder()
                .id(new UserRoleId(admin.getId(), roleAdmin.getId()))
                .user(admin)
                .role(roleAdmin)
                .build();
        admin.getUserRoles().add(urAdmin);
        admin = userRepository.save(admin);

        invoice = new Invoice();
        invoice.setReferenceNumber("INV-12345");
        invoice.setSupplierName("Supplier A");
        invoice.setSupplierEmail("supplierA@mail.com");
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setAmount(new BigDecimal("1500.00"));
        invoice.setCurrency("EUR");
        invoice.setDescription("Desc");
        invoice.setDepartment(dept);
        invoice.setSubmittedBy(assistant);
        invoice.setStatus(InvoiceStatus.BON_A_PAYER);
        invoice = invoiceRepository.save(invoice);
    }

    /**
     * AUDIT-030 (D3) : le paiement laisse desormais la facture au statut PAYE. L'archivage n'est
     * plus un effet de bord du reglement mais une action documentaire explicite
     * (POST /workflow/archive). Ce test assertait auparavant ARCHIVE.
     */
    @Test
    void recordPayment_SuccessAndLeavesInvoiceInPaye() throws Exception {
        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1500.00"),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "REFXYZ",
                null
        );

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPaid").value(1500.00))
                .andExpect(jsonPath("$.data.reference").value("REFXYZ"));

        // AUDIT-030 : PAYE est desormais un etat de repos observable, pas un etat traverse en 23 ms.
        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PAYE, updatedInvoice.getStatus());
    }

    /**
     * AUDIT-029 (D2) : paiement integral obligatoire. Le scenario exact constate en P3 — 1 XAF
     * soldant une facture de plusieurs centaines de milliers — doit desormais etre refuse, et la
     * facture rester en BON_A_PAYER (aucun paiement enregistre, donc aucun blocage definitif).
     */
    @Test
    void recordPayment_RejectsPartialAmount() throws Exception {
        PaymentRequest partial = new PaymentRequest(
                new BigDecimal("1"), PaymentMethod.VIREMENT, Instant.now(), "REF-PARTIAL", null);

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(partial)))
                .andExpect(status().is4xxClientError());

        Invoice unchanged = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.BON_A_PAYER, unchanged.getStatus());
    }

    @Test
    void recordPayment_AcceptsMobileMoney() throws Exception {
        // A2 (PROB-055): the PaymentsPage offers a "Mobile Money" method. The backend enum must
        // accept MOBILE_MONEY (sent as a raw JSON string, exactly as the frontend serializes it)
        // instead of rejecting it with a 400 Jackson deserialization error.
        String body = """
                {"amountPaid":1500.00,"paymentMethod":"MOBILE_MONEY","paymentDate":"%s","reference":"REF-MM"}
                """.formatted(Instant.now().toString());

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMethod").value("MOBILE_MONEY"));
    }

    @Test
    void recordPayment_FailsIfSoumis() throws Exception {
        invoice.setStatus(InvoiceStatus.SOUMIS);
        invoiceRepository.save(invoice);

        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1500.00"),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "REFXYZ",
                null
        );

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .header("Accept-Language", "en")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                // N17: the business message is now an i18n key resolved by locale (EN requested here).
                .andExpect(jsonPath("$.message").value("Payment can only be recorded for invoices in BON_A_PAYER status."));
    }

    @Test
    void recordPayment_ForbiddenForAuditeur() throws Exception {
        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1500.00"),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "REFXYZ",
                null
        );

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(auditeur, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITEUR")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    private void recordOnePayment() throws Exception {
        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1500.00"), PaymentMethod.VIREMENT, Instant.now(), "REF-EXP", null);
        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void exportPayments_csv_returnsFileWithRow() throws Exception {
        recordOnePayment();

        mockMvc.perform(get("/api/v1/payments/export").param("format", "csv")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE"))))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("payments.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Référence facture")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("INV-12345")));
    }

    @Test
    void exportPayments_departmentFilter_excludesOtherDept() throws Exception {
        recordOnePayment();

        // Le paiement vit dans le département "IT" ; un filtre sur un dept inexistant ne renvoie aucune ligne.
        mockMvc.perform(get("/api/v1/payments/export")
                        .param("format", "csv").param("departmentCode", "NOPE")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE"))))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("INV-12345"))));
    }

    @Test
    void exportPayments_forbiddenForAuditeur() throws Exception {
        mockMvc.perform(get("/api/v1/payments/export").param("format", "csv")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(auditeur, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITEUR"))))))
                .andExpect(status().isForbidden());
    }

    private void recordOneScheduledPayment() throws Exception {
        String body = """
                {"amountPaid":1500.00,"paymentMethod":"VIREMENT","paymentDate":"%s","reference":"REF-SCHED","scheduled":true}
                """.formatted(Instant.now().toString());
        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void processPayment_asAssistant_returns200() throws Exception {
        recordOneScheduledPayment();
        var payment = paymentRepository.findByInvoiceId(invoice.getId()).orElseThrow();

        mockMvc.perform(post("/api/v1/payments/" + payment.getId() + "/process")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE"))))))
                .andExpect(status().isOk());
    }

    @Test
    void processPayment_asAuditeur_returns403() throws Exception {
        recordOneScheduledPayment();
        var payment = paymentRepository.findByInvoiceId(invoice.getId()).orElseThrow();

        mockMvc.perform(post("/api/v1/payments/" + payment.getId() + "/process")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(auditeur, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITEUR"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordPayment_FailsIfDuplicate() throws Exception {
        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1500.00"),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "REFXYZ",
                null
        );

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Restore status to simulate attempting to pay again (though normally not allowed by status, 
        // the duplicate check applies if another thread races or something)
        Invoice freshInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        freshInvoice.setStatus(InvoiceStatus.BON_A_PAYER);
        invoiceRepository.save(freshInvoice);

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .header("Accept-Language", "en")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                // N17: business message resolved via locale (EN requested here).
                .andExpect(jsonPath("$.message").value("Payment already recorded for this invoice."));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PROB-065 (MAJEUR-2): Separation of Duties — ADMIN must NOT read payment data.
    // ReportController already excludes ADMIN; the 4 PaymentController GET endpoints
    // must match. DAF must retain read access (fix must not be over-broad).
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getPaymentByInvoiceId_forbiddenForAdmin() throws Exception {
        recordOnePayment();

        mockMvc.perform(get("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(admin, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPaymentByInvoiceId_allowedForDaf() throws Exception {
        recordOnePayment();

        mockMvc.perform(get("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(daf, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_DAF"))))))
                .andExpect(status().isOk());
    }

    @Test
    void listPayments_forbiddenForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(admin, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRemittanceDownloadUrl_forbiddenForAdmin() throws Exception {
        // recordOnePayment() auto-generates the remittance advice (PaymentServiceImpl:120-121)
        recordOnePayment();
        var payment = paymentRepository.findByInvoiceId(invoice.getId()).orElseThrow();

        mockMvc.perform(get("/api/v1/payments/" + payment.getId() + "/remittance")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(admin, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportPayments_forbiddenForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/payments/export").param("format", "csv")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(admin, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // N23 (i18n) : les messages de validation de PaymentRequest doivent être
    // renvoyés dans la langue de l'appelant (Accept-Language), pas figés en anglais.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void recordPayment_validationMessages_localizedInFrench() throws Exception {
        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .header("Accept-Language", "fr")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                // Le message français doit apparaître ; l'anglais littéral ne doit PAS.
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("obligatoire"))))
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("Payment method is required")))));
    }

    @Test
    void recordPayment_validationMessages_localizedInEnglish() throws Exception {
        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .header("Accept-Language", "en")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("is required"))));
    }
}
