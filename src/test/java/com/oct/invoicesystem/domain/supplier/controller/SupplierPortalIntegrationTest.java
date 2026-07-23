package com.oct.invoicesystem.domain.supplier.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.auth.dto.LoginRequest;
import com.oct.invoicesystem.domain.auth.dto.SupplierRegistrationRequest;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceCreateRequest;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocumentType;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SupplierPortalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private com.oct.invoicesystem.domain.supplier.repository.SupplierRepository supplierRepository;

    @Autowired
    private com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private com.oct.invoicesystem.domain.purchasing.repository.MatchingConfigRepository matchingConfigRepository;

    @Autowired
    private com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository matchingResultRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.oct.invoicesystem.domain.storage.service.MinioStorageService minioStorageService;

    @BeforeEach
    void setUp() {
        if (roleRepository.findByName("ROLE_SUPPLIER").isEmpty()) {
            roleRepository.save(Role.builder()
                    .name("ROLE_SUPPLIER")
                    .description("Supplier Portal User")
                    .build());
        }
    }

    @Test
    void fullSupplierFlow_IntegrationTest() throws Exception {
        // Create a department with all required fields
        Department dept = departmentRepository.save(Department.builder()
                .nameFr("Département Test")
                .nameEn("Test Department")
                .code("TDEPT-" + UUID.randomUUID().toString().substring(0, 8))
                .requiresN2(false)
                .n1Role("ROLE_RESPONSABLE_INFO") // Example role
                .isActive(true)
                .build());

        String email = "test-supplier-" + UUID.randomUUID() + "@example.com";
        String password = "SecurePassword123!";

        // 1. Registration
        SupplierRegistrationRequest regReq = new SupplierRegistrationRequest(
                "Test Supplier Co",
                "TAX-" + UUID.randomUUID(),
                email,
                password,
                "Supplier",
                "User",
                "+123456789",
                "BANK-DETAILS-X",
                "123 Supplier Lane"
        );

        mockMvc.perform(post("/api/v1/auth/register/supplier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andDo(print())
                .andExpect(status().isOk());

        // 2. Email Verification (Extract token from DB)
        User user = userRepository.findByEmail(email).orElseThrow();
        String token = user.getEmailVerificationToken();
        assertThat(token).isNotNull();

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", token))
                .andDo(print())
                .andExpect(status().isOk());

        // 3. Login
        LoginRequest loginReq = new LoginRequest(email, password);
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn().getResponse().getContentAsString();

        String jwt = objectMapper.readTree(loginResponse).get("data").get("accessToken").asText();
        String authHeader = "Bearer " + jwt;

        // 4. Submit Invoice
        InvoiceCreateRequest invoiceReq = new InvoiceCreateRequest(
                dept.getId(),
                null, // supplierId will be overwritten by service
                null, null, null, null, // supplier info fields
                BigDecimal.valueOf(1500.50),
                "XAF",
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                "Service delivery for March"
        );

        String submitResponse = mockMvc.perform(post("/api/v1/supplier/invoices")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceReq)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value(InvoiceStatus.BROUILLON.name()))
                .andExpect(jsonPath("$.data.amount").value(1500.50))
                .andReturn().getResponse().getContentAsString();

        String referenceNumber = objectMapper.readTree(submitResponse).get("data").get("referenceNumber").asText();

        // 5. Track Status (List Invoices)
        mockMvc.perform(get("/api/v1/supplier/invoices")
                        .header("Authorization", authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 6. Upload Document
        MockMultipartFile file = new MockMultipartFile(
                "file", "tax_cert.pdf", "application/pdf", "dummy content".getBytes());
        
        mockMvc.perform(multipart("/api/v1/supplier/documents")
                        .file(file)
                        .param("documentType", SupplierDocumentType.TAX_CERTIFICATE.name())
                        .header("Authorization", authHeader))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.filename").value("tax_cert.pdf"));

        // 7. Get Dashboard Stats - an invoice under AA control must still count as pending
        Invoice invoice = invoiceRepository.findByReferenceNumber(referenceNumber).orElseThrow();
        invoice.setStatus(InvoiceStatus.EN_CONTROLE_AA);
        invoiceRepository.save(invoice);

        mockMvc.perform(get("/api/v1/supplier/dashboard")
                        .header("Authorization", authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCounts").exists())
                .andExpect(jsonPath("$.data.matchingStatusBreakdown").exists())
                .andExpect(jsonPath("$.data.pendingCount").value(1));

        // 8. Get Profile
        mockMvc.perform(get("/api/v1/supplier/profile")
                        .header("Authorization", authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("Test Supplier Co"))
                .andExpect(jsonPath("$.data.bankDetails").doesNotExist())
                .andExpect(jsonPath("$.data.bank_details").doesNotExist());
    }

    @Test
    void expiredToken_ReturnsError() throws Exception {
        String email = "expired@example.com";
        SupplierRegistrationRequest regReq = new SupplierRegistrationRequest(
                "Expired Corp", "TAX-EXP", email, "Pass123!", "Exp", "Usr", "+1", "Bank", "Addr"
        );
        mockMvc.perform(post("/api/v1/auth/register/supplier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerificationTokenExpiry(java.time.Instant.now().minusSeconds(3600)); // 1h ago
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", user.getEmailVerificationToken()))
                .andExpect(status().isBadRequest()) // Now throws ValidationException -> 400
                .andExpect(jsonPath("$.message").value("Verification token has expired"));
    }

    // ---- V1-B: AUDIT-031 (lineItems dropped by the portal) + AUDIT-001 (no PO selector) ----

    /**
     * Registers, verifies and logs in a fresh supplier, returning its {@code Authorization} header.
     */
    private String registerAndLogin(String companyName) throws Exception {
        String email = "po-supplier-" + UUID.randomUUID() + "@example.com";
        String password = "SecurePassword123!";
        SupplierRegistrationRequest regReq = new SupplierRegistrationRequest(
                companyName, "TAX-" + UUID.randomUUID(), email, password,
                "Supplier", "User", "+123456789", "BANK-DETAILS-X", "123 Supplier Lane");
        mockMvc.perform(post("/api/v1/auth/register/supplier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", user.getEmailVerificationToken()))
                .andExpect(status().isOk());

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(loginResponse).get("data").get("accessToken").asText();
    }

    /** Resolves the supplier entity linked to the account behind {@code authHeader}. */
    private com.oct.invoicesystem.domain.supplier.model.Supplier supplierOf(String authHeader) throws Exception {
        String profile = mockMvc.perform(get("/api/v1/supplier/profile").header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID supplierId = UUID.fromString(objectMapper.readTree(profile).get("data").get("id").asText());
        return supplierRepository.findById(supplierId).orElseThrow();
    }

    /** Persists an OPEN purchase order with one line, owned by {@code supplier}. */
    private com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder openPurchaseOrder(
            com.oct.invoicesystem.domain.supplier.model.Supplier supplier, String poNumber) {
        User creator = userRepository.findAll().stream().findFirst().orElseThrow();
        var po = com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder.builder()
                .poNumber(poNumber)
                .supplier(supplier)
                .totalAmount(BigDecimal.valueOf(150000))
                .status(com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderStatus.OPEN)
                .createdBy(creator)
                .build();
        var item = com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .itemDescription("Prestation de maintenance")
                .quantity(BigDecimal.valueOf(3))
                .unitPrice(BigDecimal.valueOf(50000))
                .lineTotal(BigDecimal.valueOf(150000))
                .build();
        po.getItems().add(item);
        return purchaseOrderRepository.save(po);
    }

    /**
     * The H2 test profile carries no seeded {@code matching_config}, so the matching engine bails
     * out with "No active matching configuration found" before it ever compares lines. GRN is not
     * required here: this test targets the invoice lines, not goods receipt.
     */
    private void seedActiveMatchingConfig() {
        if (matchingConfigRepository.findByIsActiveTrue().isPresent()) {
            return;
        }
        User updater = userRepository.findAll().stream().findFirst().orElseThrow();
        matchingConfigRepository.save(
                com.oct.invoicesystem.domain.purchasing.model.MatchingConfig.builder()
                        .tolerancePercentage(new BigDecimal("2.00"))
                        .toleranceAmount(BigDecimal.ZERO)
                        .requireGrn(false)
                        .isActive(true)
                        .updatedBy(updater)
                        .build());
    }

    private Department testDepartment() {
        return departmentRepository.save(Department.builder()
                .nameFr("Département Test")
                .nameEn("Test Department")
                .code("TDEPT-" + UUID.randomUUID().toString().substring(0, 8))
                .requiresN2(false)
                .n1Role("ROLE_RESPONSABLE_INFO")
                .isActive(true)
                .build());
    }

    @Test
    void portalInvoiceWithLines_persistsThem() throws Exception {
        // AUDIT-031: the portal accepted lineItems then threw them away, so invoice_items stayed
        // empty and any PO-backed invoice was rejected at submission.
        String authHeader = registerAndLogin("Lines Supplier Co");
        Department dept = testDepartment();

        InvoiceCreateRequest req = new InvoiceCreateRequest(
                dept.getId(), null, null, null, null, null, null,
                BigDecimal.valueOf(150000), "XAF",
                LocalDate.now(), LocalDate.now().plusDays(30), "Facture avec lignes",
                java.util.List.of(
                        new InvoiceCreateRequest.LineItem("Prestation de maintenance",
                                BigDecimal.valueOf(3), BigDecimal.valueOf(50000), BigDecimal.valueOf(150000))));

        String response = mockMvc.perform(post("/api/v1/supplier/invoices")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID invoiceId = UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());
        Invoice persisted = invoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(persisted.getItems())
                .as("les lignes du portail doivent etre persistees (AUDIT-031)")
                .hasSize(1);
        assertThat(persisted.getItems().get(0).getDescription()).isEqualTo("Prestation de maintenance");
        assertThat(persisted.getItems().get(0).getLineNumber()).isEqualTo(1);
    }

    @Test
    void purchaseOrders_returnsOnlyOwnOpenOrders() throws Exception {
        // AUDIT-001 + AUDIT-002: the selector must be scoped to the authenticated supplier.
        String mineHeader = registerAndLogin("Mine Supplier Co");
        String otherHeader = registerAndLogin("Other Supplier Co");
        var mine = supplierOf(mineHeader);
        var other = supplierOf(otherHeader);

        openPurchaseOrder(mine, "PO-MINE-" + UUID.randomUUID().toString().substring(0, 8));
        var closed = openPurchaseOrder(mine, "PO-CLOSED-" + UUID.randomUUID().toString().substring(0, 8));
        closed.setStatus(com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderStatus.CLOSED);
        purchaseOrderRepository.save(closed);
        openPurchaseOrder(other, "PO-OTHER-" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(get("/api/v1/supplier/purchase-orders").header("Authorization", mineHeader))
                .andExpect(status().isOk())
                // only the OPEN PO of this supplier: not the CLOSED one, not the other supplier's
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].poNumber").value(org.hamcrest.Matchers.startsWith("PO-MINE-")))
                .andExpect(jsonPath("$.data[0].items.length()").value(1))
                // internal OCT data must not leak to the supplier
                .andExpect(jsonPath("$.data[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$.data[0].supplierId").doesNotExist());
    }

    @Test
    void portalInvoiceAgainstOwnPo_reachesMatchedOnSubmit() throws Exception {
        // Full journey (AUDIT-031 then AUDIT-001): pick own PO -> submit lines matching it ->
        // the three-way matching runs instead of rejecting the invoice for missing lines.
        String authHeader = registerAndLogin("Journey Supplier Co");
        var supplier = supplierOf(authHeader);
        seedActiveMatchingConfig();
        var po = openPurchaseOrder(supplier, "PO-JOURNEY-" + UUID.randomUUID().toString().substring(0, 8));
        Department dept = testDepartment();

        InvoiceCreateRequest req = new InvoiceCreateRequest(
                dept.getId(), null, po.getId(), null, null, null, null,
                BigDecimal.valueOf(150000), "XAF",
                LocalDate.now(), LocalDate.now().plusDays(30), "Facture adossee au PO",
                java.util.List.of(
                        new InvoiceCreateRequest.LineItem("Prestation de maintenance",
                                BigDecimal.valueOf(3), BigDecimal.valueOf(50000), BigDecimal.valueOf(150000))));

        String response = mockMvc.perform(post("/api/v1/supplier/invoices")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID invoiceId = UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());

        // An invoice cannot be submitted without a document (unrelated business rule).
        mockMvc.perform(multipart("/api/v1/supplier/invoices/" + invoiceId + "/documents")
                        .file(new MockMultipartFile("file", "facture.pdf", "application/pdf",
                                "%PDF-1.4 dummy invoice".getBytes()))
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated());
        // This test shares one transaction with the controller, so the invoice instance cached in
        // the persistence context still has an empty documents collection. Production runs each
        // request in its own transaction and does not need this.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/supplier/invoices/" + invoiceId + "/submit")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk());

        Invoice submitted = invoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(submitted.getStatus())
                .as("la soumission ne doit plus etre refusee pour absence de lignes (AUDIT-031)")
                .isNotEqualTo(InvoiceStatus.BROUILLON);
        // The three-way matching actually ran against the supplier's own PO and matched.
        assertThat(matchingResultRepository.findAll())
                .as("le rapprochement doit s'executer depuis le portail (AUDIT-001 + AUDIT-031)")
                .anyMatch(r -> invoiceId.equals(r.getInvoice().getId()));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void staff_CannotListSupplierPurchaseOrders() throws Exception {
        mockMvc.perform(get("/api/v1/supplier/purchase-orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "SUPPLIER")
    void supplier_CannotAccessStaffEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void accountant_CannotAccessSupplierPortal() throws Exception {
        mockMvc.perform(get("/api/v1/supplier/invoices"))
                .andExpect(status().isForbidden());
    }
}
