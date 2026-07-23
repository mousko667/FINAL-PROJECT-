package com.oct.invoicesystem.domain.invoice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUDIT-010 and AUDIT-037 — two "public endpoint answers 500" defects on the same surface.
 *
 * <p>AUDIT-010: {@code ?sort=} with an unknown field returned HTTP 500 on {@code /invoices},
 * {@code /departments} and — the sensitive part — {@code /supplier/invoices}, i.e. an external
 * SUPPLIER account could trigger it. All three must now answer 200 with the default ordering.</p>
 *
 * <p>AUDIT-037: {@code /invoices/pending-validation} returned 500 for all 11 validator roles
 * (LazyInitializationException on {@code Department#getCode}). One validator role is enough to lock
 * the fix, since the cause was shared by all of them.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SortAndPendingQueueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.oct.invoicesystem.domain.user.repository.UserRepository userRepository;

    @Autowired
    private com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository invoiceRepository;

    @Autowired
    private com.oct.invoicesystem.domain.department.repository.DepartmentRepository departmentRepository;

    /**
     * The list endpoints resolve the caller through SecurityHelper.currentUser, which looks the
     * username up in the database — an unknown account yields 404 and would mask the 500 these
     * tests are about. Both authenticated usernames are therefore seeded.
     */
    @org.junit.jupiter.api.BeforeEach
    void seedAccounts() {
        seed("assistant");
        seed("drh");
    }

    private void seed(String username) {
        userRepository.findByUsername(username).orElseGet(() -> {
            com.oct.invoicesystem.domain.user.model.User u = new com.oct.invoicesystem.domain.user.model.User();
            u.setUsername(username);
            u.setEmail(username + "@sort-test.local");
            u.setPassword("x");
            u.setFirstName("Sort");
            u.setLastName("Test");
            u.setActive(true);
            return userRepository.save(u);
        });
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void invoices_withUnknownSortField_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/invoices").param("sort", "nimportequoi"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void invoices_withRelationPathSort_returns200() throws Exception {
        // Used to return 200 while silently adding a join; now falls back to the default.
        mockMvc.perform(get("/api/v1/invoices").param("sort", "department.nameFr,asc"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void invoices_withAllowedSortField_stillWorks() throws Exception {
        // Counter-proof: legitimate sorting must keep working, not be flattened away.
        mockMvc.perform(get("/api/v1/invoices").param("sort", "amount,desc"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void departments_withUnknownSortField_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/departments").param("sort", "zzz"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPPLIER", username = "supplier")
    void supplierPortalInvoices_withUnknownSortField_isNoLongerA500() throws Exception {
        // The sharpest part of AUDIT-010: an EXTERNAL account could trigger the 500
        // (GET /api/v1/supplier/invoices?sort=zzz returned 500 with a SUPPLIER token). The portal
        // routes through the same InvoiceService.listInvoices, so the whitelist covers it. This
        // account is not linked to a supplier profile in the test context, so the endpoint may
        // legitimately answer 4xx — what must never happen again is a 500.
        mockMvc.perform(get("/api/v1/supplier/invoices").param("sort", "zzz"))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(statusCode)
                            .as("an unknown sort field must never produce a server error")
                            .isLessThan(500);
                });
    }

    @Test
    @WithMockUser(authorities = "ROLE_VALIDATEUR_N1_DRH", username = "drh")
    void pendingValidation_withValidatorRole_returns200() throws Exception {
        // AUDIT-037: 500 for the 11 validator roles before the fix. The queue MUST contain at least
        // one invoice for this to prove anything: the failure was a LazyInitializationException
        // raised while mapping a row, so on an empty queue the endpoint answers 200 either way.
        seedPendingInvoice();

        mockMvc.perform(get("/api/v1/invoices/pending-validation").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.content.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @WithMockUser(authorities = "ROLE_VALIDATEUR_N1_DRH", username = "drh")
    void pendingValidation_withUnknownSortField_returns200() throws Exception {
        // Third site of AUDIT-010, on the endpoint AUDIT-037 also covers.
        mockMvc.perform(get("/api/v1/invoices/pending-validation")
                        .param("size", "1")
                        .param("sort", "zzz"))
                .andExpect(status().isOk());
    }

    /**
     * Puts one invoice in EN_VALIDATION_N1 with a department attached, so the controller actually
     * maps a row (and therefore actually dereferences the lazy Department proxy).
     */
    private void seedPendingInvoice() {
        String tag = java.util.UUID.randomUUID().toString().substring(0, 6);
        com.oct.invoicesystem.domain.user.model.User submitter =
                userRepository.findByUsername("assistant").orElseThrow();
        com.oct.invoicesystem.domain.department.model.Department dept = departmentRepository.save(
                com.oct.invoicesystem.domain.department.model.Department.builder()
                        .code("SRT" + tag.substring(0, 3).toUpperCase())
                        .nameFr("Tri " + tag)
                        .nameEn("Sort " + tag)
                        .requiresN2(false)
                        .n1Role("ROLE_VALIDATEUR_N1_DRH")
                        .build());
        invoiceRepository.save(com.oct.invoicesystem.domain.invoice.model.Invoice.builder()
                .referenceNumber("SRT-" + tag)
                .department(dept)
                .submittedBy(submitter)
                .supplierName("Sort Supplier " + tag)
                .supplierEmail("sort." + tag + "@test.local")
                .amount(new java.math.BigDecimal("1000.00"))
                .currency("XAF")
                .issueDate(java.time.LocalDate.now())
                .dueDate(java.time.LocalDate.now().plusDays(30))
                .status(com.oct.invoicesystem.domain.invoice.model.InvoiceStatus.EN_VALIDATION_N1)
                .build());
    }
}
