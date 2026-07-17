package com.oct.invoicesystem.domain.purchasing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingOverrideRequest;
import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderCreateRequest;
import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderItemCreateRequest;
import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptNote;
import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptItem;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderItem;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderStatus;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import com.oct.invoicesystem.domain.purchasing.repository.GoodsReceiptNoteRepository;
import com.oct.invoicesystem.domain.purchasing.repository.MatchingConfigRepository;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Three-Way Matching Integration Tests")
@Transactional
class ThreeWayMatchingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private GoodsReceiptNoteRepository goodsReceiptNoteRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ThreeWayMatchingResultRepository matchingResultRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private MatchingConfigRepository matchingConfigRepository;

    private UUID supplierId;
    private UUID departmentId;
    private String adminToken;
    private String assistantToken;
    private User adminUser;
    private Department department;

    @BeforeEach
    void setUp() {
        // Create test admin user so controller can find by username
        adminUser = userRepository.findByUsername("admin").orElseGet(() -> {
            User u = User.builder()
                    .username("admin")
                    .email("admin@test.local")
                    .password(passwordEncoder.encode("Password!1"))
                    .firstName("Admin")
                    .lastName("Test")
                    .preferredLang("fr")
                    .build();
            return userRepository.save(u);
        });

        // Create test supplier
        Supplier supplier = Supplier.builder()
                .companyName("Test Supplier")
                .contactEmail("supplier@test.com")
                .taxId("12345678")
                .build();
        supplier = supplierRepository.save(supplier);
        supplierId = supplier.getId();

        // Seed a department
        department = departmentRepository.findByCode("TEST").orElseGet(() -> {
            Department d = new Department();
            d.setCode("TEST");
            d.setNameFr("Test Department");
            d.setNameEn("Test Department");
            d.setN1Role("ROLE_MANAGER");
            d.setActive(true);
            d.setRequiresN2(false);
            return departmentRepository.save(d);
        });
        departmentId = department.getId();

        if (matchingConfigRepository.findByIsActiveTrue().isEmpty()) {
            MatchingConfig config = new MatchingConfig();
            config.setTolerancePercentage(new BigDecimal("5.00"));
            config.setToleranceAmount(new BigDecimal("100.00"));
            config.setRequireGrn(true);
            config.setIsActive(true);
            config.setUpdatedBy(adminUser);
            matchingConfigRepository.save(config);
        }

        // Create or get test users with required roles
        Role adminRole = new Role();
        Role assistantRole = new Role();
    }

    @Test
    @DisplayName("Complete workflow: PO → GRN → Invoice → Matching → Override")
    @WithMockUser(username = "admin", roles = {"ADMIN", "ASSISTANT_COMPTABLE", "DAF"})
    void testCompleteThreeWayMatchingWorkflow() throws Exception {
        // Step 1: Create Purchase Order
        PurchaseOrderItemCreateRequest poItem = new PurchaseOrderItemCreateRequest(
                "Widget A",
                new BigDecimal("100"),
                new BigDecimal("50.00")
        );

        PurchaseOrderCreateRequest poRequest = new PurchaseOrderCreateRequest(
                "PO-TEST-001",
                supplierId,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                "EUR",
                List.of(poItem)
        );

        MvcResult poResult = mockMvc.perform(post("/api/v1/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(poRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(poResult.getResponse().getContentAsString()).contains("\"poNumber\":\"PO-TEST-001\"");

        // Step 2: Retrieve created PO (get PO ID from response)
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByPoNumber("PO-TEST-001")
                .orElseThrow(() -> new RuntimeException("PO not created"));
        UUID poId = purchaseOrder.getId();

        // Step 3: Create Goods Receipt Note with matching quantities
        GoodsReceiptNote grn = GoodsReceiptNote.builder()
                .grnNumber("GRN-TEST-001")
                .purchaseOrder(purchaseOrder)
                .receivedBy(adminUser)
                .receiptDate(LocalDate.now())
                .build();
        grn = goodsReceiptNoteRepository.save(grn);

        GoodsReceiptItem grnItem = GoodsReceiptItem.builder()
                .goodsReceiptNote(grn)
                .purchaseOrderItem(purchaseOrder.getItems().get(0))
                .receivedQuantity(new BigDecimal("100"))
                .build();
        grn.getItems().add(grnItem);
        grn = goodsReceiptNoteRepository.save(grn);
        UUID grnId = grn.getId();

        // Step 4: Create Invoice with PO reference
        InvoiceItem invoiceItem = InvoiceItem.builder()
                .lineNumber(1)
                .description("Widget A")
                .quantity(new BigDecimal("150")) // Mismatch: 150 vs 100 in PO
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("7500.00"))
                .build();

        Invoice invoice = Invoice.builder()
                .referenceNumber("FAC-TEST-001")
                .supplierName("Test Supplier")
                .supplierEmail("supplier@test.com")
                .amount(new BigDecimal("7500.00"))
                .currency("EUR")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .department(department)
                .submittedBy(adminUser)
                .purchaseOrderId(poId) // Link to PO
                .build();
        
        invoiceItem.setInvoice(invoice);
        invoice.setItems(List.of(invoiceItem));

        InvoiceDocument document = InvoiceDocument.builder()
                .originalFilename("invoice.pdf")
                .minioObjectKey("docs/invoice1.pdf")
                .fileType("application/pdf")
                .fileSizeBytes(1024L)
                .checksumSha256("hash1")
                .uploadedBy(adminUser)
                .build();
        document.setInvoice(invoice);
        invoice.setDocuments(List.of(document));

        invoice = invoiceRepository.save(invoice);
        UUID invoiceId = invoice.getId();

        // Step 5: Submit Invoice - this should trigger matching and result in MISMATCH
        mockMvc.perform(post("/api/v1/invoices/{id}/submit", invoiceId))
                .andExpect(status().isBadRequest()) // Blocked due to MISMATCH
                .andExpect(jsonPath("$.message", containsString("MISMATCH")));

        // Step 6: Verify matching result is MISMATCH
        var matchingResult = matchingResultRepository.findByInvoiceId(invoiceId)
                .orElseThrow();
        assertThat(matchingResult.getStatus().name()).contains("MISMATCH");

        // Step 7: Record override as DAF
        MatchingOverrideRequest overrideRequest = new MatchingOverrideRequest(
                "Approved by DAF: Quantity variance acceptable due to partial shipment"
        );

        mockMvc.perform(post("/api/v1/invoices/{id}/matching/override", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isOk());

        // Step 8: Verify override was recorded
        var updatedMatchingResult = matchingResultRepository.findByInvoiceId(invoiceId)
                .orElseThrow();
        assertThat(updatedMatchingResult.getOverriddenBy()).isNotNull();
        assertThat(updatedMatchingResult.getOverrideReason()).isNotNull();
    }

    @Test
    @DisplayName("Invoice with perfect matching should be allowed to submit")
    @WithMockUser(username = "admin", roles = {"ADMIN", "ASSISTANT_COMPTABLE"})
    void testPerfectMatchingAllowsSubmission() throws Exception {
        // Create PO with exact quantities
        PurchaseOrderItemCreateRequest poItem = new PurchaseOrderItemCreateRequest(
                "Widget B",
                new BigDecimal("100"),
                new BigDecimal("50.00")
        );

        PurchaseOrderCreateRequest poRequest = new PurchaseOrderCreateRequest(
                "PO-TEST-002",
                supplierId,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                "EUR",
                List.of(poItem)
        );

        mockMvc.perform(post("/api/v1/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(poRequest)))
                .andExpect(status().isCreated());

        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByPoNumber("PO-TEST-002")
                .orElseThrow();

        // Create GRN with matching quantities
        GoodsReceiptNote grn = GoodsReceiptNote.builder()
                .grnNumber("GRN-TEST-002")
                .purchaseOrder(purchaseOrder)
                .receivedBy(adminUser)
                .receiptDate(LocalDate.now())
                .build();
        grn = goodsReceiptNoteRepository.save(grn);

        GoodsReceiptItem grnItem = GoodsReceiptItem.builder()
                .goodsReceiptNote(grn)
                .purchaseOrderItem(purchaseOrder.getItems().get(0))
                .receivedQuantity(new BigDecimal("100"))
                .build();
        grn.getItems().add(grnItem);
        goodsReceiptNoteRepository.save(grn);

        // Create Invoice with identical quantities and prices
        InvoiceItem invoiceItem = InvoiceItem.builder()
                .lineNumber(1)
                .description("Widget B")
                .quantity(new BigDecimal("100")) // Perfect match
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("5000.00"))
                .build();

        Invoice invoice = Invoice.builder()
                .referenceNumber("FAC-TEST-002")
                .supplierName("Test Supplier")
                .supplierEmail("supplier@test.com")
                .amount(new BigDecimal("5000.00"))
                .currency("EUR")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .department(department)
                .submittedBy(adminUser)
                .purchaseOrderId(purchaseOrder.getId())
                .build();
                
        invoiceItem.setInvoice(invoice);
        invoice.setItems(List.of(invoiceItem));
        InvoiceDocument document = InvoiceDocument.builder()
                .originalFilename("invoice2.pdf")
                .minioObjectKey("docs/invoice2.pdf")
                .fileType("application/pdf")
                .fileSizeBytes(1024L)
                .checksumSha256("hash2")
                .uploadedBy(adminUser)
                .build();
        document.setInvoice(invoice);
        invoice.setDocuments(List.of(document));

        invoice = invoiceRepository.save(invoice);

        // Submit Invoice - should succeed with perfect match
        mockMvc.perform(post("/api/v1/invoices/{id}/submit", invoice.getId()))
                .andExpect(status().isOk()); // Should be allowed

        // Verify matching result is MATCHED
        var matchingResult = matchingResultRepository.findByInvoiceId(invoice.getId())
                .orElseThrow();
        assertThat(matchingResult.getStatus().name()).contains("MATCHED");
    }

    @Test
    @DisplayName("B2: matching reconciliation report can be exported as CSV and Excel")
    @WithMockUser(username = "admin", roles = {"ASSISTANT_COMPTABLE"})
    void testExportMatchingReport() throws Exception {
        // Build a PO + GRN + invoice and submit it so a matching result is persisted.
        PurchaseOrderCreateRequest poRequest = new PurchaseOrderCreateRequest(
                "PO-EXPORT-001", supplierId, LocalDate.now(), LocalDate.now().plusDays(30), "EUR",
                List.of(new PurchaseOrderItemCreateRequest("Widget X", new BigDecimal("100"), new BigDecimal("50.00"))));

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poRequest)))
                .andExpect(status().isCreated());

        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByPoNumber("PO-EXPORT-001").orElseThrow();

        GoodsReceiptNote grn = goodsReceiptNoteRepository.save(GoodsReceiptNote.builder()
                .grnNumber("GRN-EXPORT-001").purchaseOrder(purchaseOrder)
                .receivedBy(adminUser).receiptDate(LocalDate.now()).build());
        grn.getItems().add(GoodsReceiptItem.builder()
                .goodsReceiptNote(grn).purchaseOrderItem(purchaseOrder.getItems().get(0))
                .receivedQuantity(new BigDecimal("100")).build());
        goodsReceiptNoteRepository.save(grn);

        InvoiceItem invoiceItem = InvoiceItem.builder()
                .lineNumber(1).description("Widget X").quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00")).totalPrice(new BigDecimal("5000.00")).build();
        Invoice invoice = Invoice.builder()
                .referenceNumber("FAC-EXPORT-001").supplierName("Test Supplier")
                .supplierEmail("supplier@test.com").amount(new BigDecimal("5000.00")).currency("EUR")
                .issueDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .department(department).submittedBy(adminUser).purchaseOrderId(purchaseOrder.getId()).build();
        invoiceItem.setInvoice(invoice);
        invoice.setItems(List.of(invoiceItem));
        InvoiceDocument document = InvoiceDocument.builder()
                .originalFilename("inv.pdf").minioObjectKey("docs/inv-export.pdf").fileType("application/pdf")
                .fileSizeBytes(1024L).checksumSha256("hashx").uploadedBy(adminUser).build();
        document.setInvoice(invoice);
        invoice.setDocuments(List.of(document));
        invoice = invoiceRepository.save(invoice);

        mockMvc.perform(post("/api/v1/invoices/{id}/submit", invoice.getId()))
                .andExpect(status().isOk());

        // CSV export of the reconciliation report
        mockMvc.perform(get("/api/v1/invoices/{id}/matching/export", invoice.getId()).param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Content-Disposition"))
                        .contains("matching_report_").contains(".csv"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("FAC-EXPORT-001").contains("MATCHED"));

        // Excel export returns the spreadsheet content type
        mockMvc.perform(get("/api/v1/invoices/{id}/matching/export", invoice.getId()).param("format", "excel"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .contains("spreadsheetml"));
    }

    @Test
    @DisplayName("Get purchase order should return all items and details")
    @WithMockUser(username = "admin", roles = {"ASSISTANT_COMPTABLE"})
    void testGetPurchaseOrderWithItems() throws Exception {
        // Create PO
        PurchaseOrderItemCreateRequest poItem1 = new PurchaseOrderItemCreateRequest(
                "Item 1",
                new BigDecimal("50"),
                new BigDecimal("25.00")
        );
        PurchaseOrderItemCreateRequest poItem2 = new PurchaseOrderItemCreateRequest(
                "Item 2",
                new BigDecimal("75"),
                new BigDecimal("30.00")
        );

        PurchaseOrderCreateRequest poRequest = new PurchaseOrderCreateRequest(
                "PO-TEST-003",
                supplierId,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                "EUR",
                List.of(poItem1, poItem2)
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(poRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract PO ID from response
        String responseContent = createResult.getResponse().getContentAsString();
        assertThat(responseContent).contains("PO-TEST-003");

        // Get the PO
        PurchaseOrder po = purchaseOrderRepository.findByPoNumber("PO-TEST-003").orElseThrow();

        mockMvc.perform(get("/api/v1/purchase-orders/{id}", po.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poNumber").value("PO-TEST-003"))
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
