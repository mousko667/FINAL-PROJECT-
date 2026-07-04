package com.oct.invoicesystem.domain.supplier.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.supplier.dto.SupplierCreateRequest;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocument;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocumentType;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.supplier.repository.SupplierDocumentRepository;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupplierIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SupplierDocumentRepository supplierDocumentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateAndSuspendSupplier() throws Exception {
        // 1. Create Supplier
        SupplierCreateRequest createReq = new SupplierCreateRequest(
                "Acme Integration",
                "TAX-INT-999",
                "acme.int@example.com",
                "+123456789",
                "BANK123",
                "123 Integration St"
        );

        String createResponse = mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyName").value("Acme Integration"))
                .andExpect(jsonPath("$.data.status").value(SupplierStatus.PENDING_VERIFICATION.name()))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).get("data").get("id").asText();

        // 2. Suspend Supplier
        mockMvc.perform(patch("/api/v1/suppliers/{id}/suspend", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Verify Suspension in DB via Get
        mockMvc.perform(get("/api/v1/suppliers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(SupplierStatus.SUSPENDED.name()));

        // Cleanup
        supplierDocumentRepository.findBySupplierId(java.util.UUID.fromString(id))
                .forEach(supplierDocumentRepository::delete);
        supplierRepository.deleteById(java.util.UUID.fromString(id));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MAJEUR-11 (SoD, PROB-065 family): ADMIN must NOT read supplier performance
    // metrics (financial data) — DAF/ASSISTANT_COMPTABLE only, matching ReportController.
    // MAJEUR-12: getPerformanceMetrics must NOT fabricate metrics on
    // ResourceNotFoundException (e.g. supplier with no invoices, or unknown id) —
    // it must propagate to a real 404, never a fake 200.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPerformanceMetrics_forbiddenForAdmin() throws Exception {
        String id = createSupplier("Perf Admin Co", "TAX-PERF-ADMIN-001", "perf.admin@example.com");
        try {
            mockMvc.perform(get("/api/v1/suppliers/{id}/performance", id))
                    .andExpect(status().isForbidden());
        } finally {
            cleanupSupplier(id);
        }
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getPerformanceMetrics_notFoundForUnknownSupplier_doesNotFabricateMetrics() throws Exception {
        java.util.UUID unknownId = java.util.UUID.randomUUID();

        mockMvc.perform(get("/api/v1/suppliers/{id}/performance", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getPerformanceMetrics_notFoundForSupplierWithNoInvoices_doesNotFabricateMetrics() throws Exception {
        // A supplier with zero invoices makes ReportService.getSupplierPerformance throw
        // ResourceNotFoundException. Before the fix, the controller caught this and returned
        // a fake 200 (accuracy=1.0, rejection=0.0, ...). It must now propagate to a real 404.
        String id = createSupplier("Perf NoInvoice Co", "TAX-PERF-NOINV-001", "perf.noinv@example.com");
        try {
            mockMvc.perform(get("/api/v1/suppliers/{id}/performance", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            cleanupSupplier(id);
        }
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getPerformanceMetrics_returnsRealMetricsForAuthorizedRole() throws Exception {
        // MAJEUR-11/12 follow-up: an authorized role (DAF) with a supplier that HAS invoices
        // must get a real 200 with real metrics — not a 403 (SoD scope) and not a 404
        // (no-fabrication scope). This is the positive path the two negative tests don't cover.
        //
        // Two invoices are seeded: one MISMATCH and one REJETE, so the computed
        // accuracyRate (0.5) and rejectionRate (0.5) are genuinely derived from the fixture data,
        // not the old fabricated constants (accuracy=1.0, rejection=0.0) that the bug always
        // returned regardless of actual invoice state.
        String id = createSupplier("Perf DAF Co", "TAX-PERF-DAF-001", "perf.daf@example.com");
        java.util.UUID supplierId = java.util.UUID.fromString(id);
        java.util.List<java.util.UUID> invoiceIds = new java.util.ArrayList<>();
        try {
            Department department = departmentRepository.findAll().stream().findFirst()
                    .orElseGet(() -> departmentRepository.save(Department.builder()
                            .code("DEPT-PERF")
                            .nameFr("Departement Perf")
                            .nameEn("Perf Department")
                            .n1Role("ROLE_MANAGER")
                            .isActive(true)
                            .requiresN2(false)
                            .build()));

            User submitter = userRepository.findByUsername("perf_daf_submitter").orElseGet(() -> {
                User u = new User();
                u.setUsername("perf_daf_submitter");
                u.setEmail("perf.daf.submitter@test.com");
                u.setPassword("password");
                u.setFirstName("Perf");
                u.setLastName("Submitter");
                u.setActive(true);
                return userRepository.save(u);
            });

            Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(supplierId).orElseThrow();

            Invoice mismatchInvoice = Invoice.builder()
                    .referenceNumber("FAC-PERF-DAF-001")
                    .department(department)
                    .submittedBy(submitter)
                    .supplier(supplier)
                    .supplierName("Perf DAF Co")
                    .supplierEmail("perf.daf@example.com")
                    .amount(BigDecimal.valueOf(1000))
                    .currency("EUR")
                    .status(InvoiceStatus.BON_A_PAYER)
                    .matchingStatus("MISMATCH")
                    .issueDate(LocalDate.now().minusDays(20))
                    .dueDate(LocalDate.now().minusDays(5))
                    .build();
            invoiceIds.add(invoiceRepository.save(mismatchInvoice).getId());

            Invoice rejectedInvoice = Invoice.builder()
                    .referenceNumber("FAC-PERF-DAF-002")
                    .department(department)
                    .submittedBy(submitter)
                    .supplier(supplier)
                    .supplierName("Perf DAF Co")
                    .supplierEmail("perf.daf@example.com")
                    .amount(BigDecimal.valueOf(500))
                    .currency("EUR")
                    .status(InvoiceStatus.REJETE)
                    .issueDate(LocalDate.now().minusDays(10))
                    .dueDate(LocalDate.now().plusDays(10))
                    .build();
            invoiceIds.add(invoiceRepository.save(rejectedInvoice).getId());

            mockMvc.perform(get("/api/v1/suppliers/{id}/performance", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.supplierId").value(id))
                    .andExpect(jsonPath("$.data.totalInvoicesSubmitted").value(2))
                    .andExpect(jsonPath("$.data.mismatchedInvoices").value(1))
                    .andExpect(jsonPath("$.data.invoiceAccuracyRate").value(0.5))
                    .andExpect(jsonPath("$.data.rejectionRate").value(0.5));
        } finally {
            invoiceIds.forEach(invoiceRepository::deleteById);
            cleanupSupplier(id);
        }
    }

    private String createSupplier(String companyName, String taxId, String email) throws Exception {
        SupplierCreateRequest createReq = new SupplierCreateRequest(
                companyName, taxId, email, "+123456789", "BANK123", "123 Integration St");

        String createResponse = mockMvc.perform(post("/api/v1/suppliers")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(createResponse).get("data").get("id").asText();
    }

    private void cleanupSupplier(String id) {
        java.util.UUID uuid = java.util.UUID.fromString(id);
        supplierDocumentRepository.findBySupplierId(uuid).forEach(supplierDocumentRepository::delete);
        supplierRepository.deleteById(uuid);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldActivateSupplierAfterOnboardingDocumentsArePresent() throws Exception {
        SupplierCreateRequest createReq = new SupplierCreateRequest(
                "Onboarded Integration",
                "TAX-INT-ACT-001",
                "onboarded.int@example.com",
                "+123456780",
                "BANK-ACT-001",
                "1 Activation St"
        );

        String createResponse = mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value(SupplierStatus.PENDING_VERIFICATION.name()))
                .andReturn().getResponse().getContentAsString();

        java.util.UUID supplierId = java.util.UUID.fromString(objectMapper.readTree(createResponse).get("data").get("id").asText());
        User admin = userRepository.findByUsername("admin").orElseGet(() -> {
            User u = new User();
            u.setUsername("admin");
            u.setEmail("admin@test.com");
            u.setPassword("password");
            u.setFirstName("Admin");
            u.setLastName("User");
            u.setActive(true);
            return userRepository.save(u);
        });

        supplierDocumentRepository.save(SupplierDocument.builder()
                .supplier(supplierRepository.findByIdAndDeletedAtIsNull(supplierId).orElseThrow())
                .documentType(SupplierDocumentType.TAX_CERTIFICATE)
                .originalFilename("tax.pdf")
                .minioObjectKey("supplier-docs/" + supplierId + "/tax.pdf")
                .fileSizeBytes(123L)
                .checksumSha256("a".repeat(64))
                .uploadedBy(admin)
                .build());
        supplierDocumentRepository.save(SupplierDocument.builder()
                .supplier(supplierRepository.findByIdAndDeletedAtIsNull(supplierId).orElseThrow())
                .documentType(SupplierDocumentType.CONTRACT)
                .originalFilename("contract.pdf")
                .minioObjectKey("supplier-docs/" + supplierId + "/contract.pdf")
                .fileSizeBytes(456L)
                .checksumSha256("b".repeat(64))
                .uploadedBy(admin)
                .build());

        mockMvc.perform(patch("/api/v1/suppliers/{id}/activate", supplierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/suppliers/{id}", supplierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(SupplierStatus.ACTIVE.name()));

        supplierDocumentRepository.findBySupplierId(supplierId).forEach(supplierDocumentRepository::delete);
        supplierRepository.deleteById(supplierId);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRejectActivationWhenOnboardingIsIncomplete() throws Exception {
        SupplierCreateRequest createReq = new SupplierCreateRequest(
                "Incomplete Integration",
                "TAX-INT-MISS-001",
                "incomplete.int@example.com",
                "+123456781",
                "BANK-MISS-001",
                "2 Missing St"
        );

        String createResponse = mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        java.util.UUID supplierId = java.util.UUID.fromString(objectMapper.readTree(createResponse).get("data").get("id").asText());

        userRepository.findByUsername("admin").orElseGet(() -> {
            User u = new User();
            u.setUsername("admin");
            u.setEmail("admin@test.com");
            u.setPassword("password");
            u.setFirstName("Admin");
            u.setLastName("User");
            u.setActive(true);
            return userRepository.save(u);
        });

        mockMvc.perform(patch("/api/v1/suppliers/{id}/activate", supplierId))
                .andExpect(status().isBadRequest());

        supplierRepository.deleteById(supplierId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldPersistAndFilterByCategory() throws Exception {
        // B5: a supplier carries a spend-type category (GOODS/SERVICES/WORKS/CONSULTING) that
        // round-trips through create and acts as a directory filter.
        SupplierCreateRequest createReq = new SupplierCreateRequest(
                "Cat Services Co", "TAX-CAT-001", "cat.services@example.com",
                "+100000000", "BANKCAT", "1 Cat St",
                com.oct.invoicesystem.domain.supplier.model.SupplierCategory.SERVICES);

        String createResponse = mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("SERVICES"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createResponse).get("data").get("id").asText();

        // Filter by the matching category → supplier is present
        mockMvc.perform(get("/api/v1/suppliers").param("category", "SERVICES").param("taxId", "TAX-CAT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].category").value("SERVICES"));

        // Filter by a different category → supplier is filtered out
        mockMvc.perform(get("/api/v1/suppliers").param("category", "GOODS").param("taxId", "TAX-CAT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        supplierDocumentRepository.findBySupplierId(java.util.UUID.fromString(id))
                .forEach(supplierDocumentRepository::delete);
        supplierRepository.deleteById(java.util.UUID.fromString(id));
    }
}
