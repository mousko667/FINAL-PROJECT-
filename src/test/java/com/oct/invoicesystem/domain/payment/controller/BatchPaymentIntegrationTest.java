package com.oct.invoicesystem.domain.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.payment.dto.BatchPaymentRequest;
import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration coverage for batch payments (B3). NOT @Transactional on purpose: the batch service
 * commits each line in its own transaction, so the test must really commit to exercise the
 * best-effort (partial success) behaviour. Seeded rows are removed in {@link #cleanup()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BatchPaymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockBean private MinioStorageService minioStorageService;

    private User assistant;
    private Department dept;
    private Invoice payable;
    private Invoice notPayable;

    @BeforeEach
    void setUp() {
        dept = departmentRepository.findByCode("BATCH").orElseGet(() -> {
            Department d = new Department();
            d.setCode("BATCH");
            d.setNameFr("Batch Dept");
            d.setNameEn("Batch Dept");
            d.setN1Role("ROLE_MANAGER");
            d.setActive(true);
            d.setRequiresN2(false);
            return departmentRepository.save(d);
        });

        Role roleAssistant = roleRepository.findByName("ROLE_ASSISTANT_COMPTABLE").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_ASSISTANT_COMPTABLE");
            return roleRepository.save(r);
        });

        assistant = new User();
        assistant.setUsername("batch-assistant");
        assistant.setEmail("batch-assistant@oct.test");
        assistant.setPassword(passwordEncoder.encode("Password123!"));
        assistant.setFirstName("Batch");
        assistant.setLastName("Assistant");
        assistant.setMfaEnabled(true);
        assistant.setMfaVerified(true);
        assistant = userRepository.save(assistant);
        UserRole ur = UserRole.builder()
                .id(new UserRoleId(assistant.getId(), roleAssistant.getId()))
                .user(assistant).role(roleAssistant).build();
        assistant.getUserRoles().add(ur);
        userRepository.save(assistant);
        // Re-fetch with roles eagerly loaded so the detached security principal can resolve its
        // authorities outside a session (the test is non-transactional).
        assistant = userRepository.findByUsername("batch-assistant").orElseThrow();

        payable = saveInvoice("FAC-BATCH-OK", InvoiceStatus.BON_A_PAYER);
        notPayable = saveInvoice("FAC-BATCH-KO", InvoiceStatus.SOUMIS); // wrong status → line fails
    }

    private Invoice saveInvoice(String ref, InvoiceStatus status) {
        Invoice i = new Invoice();
        i.setReferenceNumber(ref);
        i.setSupplierName("Batch Supplier");
        i.setSupplierEmail("batch.supplier@oct.test");
        i.setAmount(new BigDecimal("2500.00"));
        i.setCurrency("XAF");
        i.setIssueDate(LocalDate.now());
        i.setDueDate(LocalDate.now().plusDays(30));
        i.setDepartment(dept);
        i.setSubmittedBy(assistant);
        i.setStatus(status);
        return invoiceRepository.save(i);
    }

    @AfterEach
    void cleanup() {
        // The test is non-transactional and the paid invoice now has child rows (payment,
        // status history, remittance), so hard-deleting the invoice would violate FKs. Soft-delete
        // the invoices instead (as the app does) — that is enough to keep them out of other tests'
        // queries. Best-effort: never let cleanup mask the test result.
        try {
            invoiceRepository.findByIdAndDeletedAtIsNull(payable.getId())
                    .ifPresent(i -> { i.setDeletedAt(Instant.now()); invoiceRepository.save(i); });
            invoiceRepository.findByIdAndDeletedAtIsNull(notPayable.getId())
                    .ifPresent(i -> { i.setDeletedAt(Instant.now()); invoiceRepository.save(i); });
        } catch (Exception ignored) {
            // hygiene only
        }
    }

    @Test
    void batchPayment_paysValidInvoices_andReportsFailuresPerLine() throws Exception {
        BatchPaymentRequest request = new BatchPaymentRequest(
                List.of(payable.getId(), notPayable.getId()),
                PaymentMethod.VIREMENT, Instant.now());

        mockMvc.perform(post("/api/v1/payments/batch")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.succeeded").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.results[?(@.invoiceId == '" + payable.getId() + "')].success").value(true))
                .andExpect(jsonPath("$.data.results[?(@.invoiceId == '" + notPayable.getId() + "')].success").value(false));
    }
}
