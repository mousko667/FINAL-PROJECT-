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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void recordPayment_SuccessAndTriggersArchive() throws Exception {
        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1500.00"),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "REFXYZ"
        );

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPaid").value(1500.00))
                .andExpect(jsonPath("$.data.reference").value("REFXYZ"));

        // Verify status changed to ARCHIVE
        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.ARCHIVE, updatedInvoice.getStatus());
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
                "REFXYZ"
        );

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment can only be recorded for invoices in BON_A_PAYER status"));
    }

    @Test
    void recordPayment_ForbiddenForAuditeur() throws Exception {
        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1500.00"),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "REFXYZ"
        );

        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(auditeur, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITEUR")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordPayment_FailsIfDuplicate() throws Exception {
        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1500.00"),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "REFXYZ"
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
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment already recorded for this invoice"));
    }
}
