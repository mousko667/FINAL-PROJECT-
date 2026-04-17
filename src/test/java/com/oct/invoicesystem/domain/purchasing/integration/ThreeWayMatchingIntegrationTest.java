package com.oct.invoicesystem.domain.purchasing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
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
import com.oct.invoicesystem.domain.purchasing.repository.GoodsReceiptNoteRepository;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
@TestPropertySource(properties = {
        "spring.test.mockmvc.print=true"
})
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
    private ThreeWayMatchingResultRepository matchingResultRepository;

    private UUID supplierId;
    private UUID departmentId;
    private String adminToken;
    private String assistantToken;

    @BeforeEach
    void setUp() {
        // Create test supplier
        Supplier supplier = Supplier.builder()
                .companyName("Test Supplier")
                .email("supplier@test.com")
                .taxId("12345678")
                .country("FR")
                .currency("EUR")
                .build();
        supplier = supplierRepository.save(supplier);
        supplierId = supplier.getId();

        // Create test department
        departmentId = UUID.randomUUID(); // Assumes department exists from seed data

        // Create or get test users with required roles
        Role adminRole = new Role();
        Role assistantRole = new Role();
    }

    @Test
    @DisplayName("Complete workflow: PO → GRN → Invoice → Matching → Override")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
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

        assertThat(poResult.getResponse().getContentAsString()).contains("\"po_number\":\"PO-TEST-001\"");

        // Step 2: Retrieve created PO (get PO ID from response)
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByPoNumber("PO-TEST-001")
                .orElseThrow(() -> new RuntimeException("PO not created"));
        UUID poId = purchaseOrder.getId();

        // Step 3: Create Goods Receipt Note with matching quantities
        GoodsReceiptItem grnItem = GoodsReceiptItem.builder()
                .purchaseOrderItem(purchaseOrder.getItems().get(0))
                .receivedQuantity(new BigDecimal("100"))
                .build();

        GoodsReceiptNote grn = GoodsReceiptNote.builder()
                .grnNumber("GRN-TEST-001")
                .purchaseOrder(purchaseOrder)
                .items(List.of(grnItem))
                .build();
        grn = goodsReceiptNoteRepository.save(grn);
        UUID grnId = grn.getId();

        // Step 4: Create Invoice with PO reference
        InvoiceItem invoiceItem = InvoiceItem.builder()
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
                .items(List.of(invoiceItem))
                .purchaseOrderId(poId) // Link to PO
                .build();

        invoice = invoiceRepository.save(invoice);
        UUID invoiceId = invoice.getId();

        // Step 5: Submit Invoice - this should trigger matching and result in MISMATCH
        mockMvc.perform(post("/api/v1/invoices/{id}/submit", invoiceId))
                .andExpect(status().isBadRequest()) // Blocked due to MISMATCH
                .andExpect(jsonPath("$.message", containsString("mismatch")));

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
    @WithMockUser(username = "admin", roles = {"ADMIN"})
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
        GoodsReceiptItem grnItem = GoodsReceiptItem.builder()
                .purchaseOrderItem(purchaseOrder.getItems().get(0))
                .receivedQuantity(new BigDecimal("100"))
                .build();

        GoodsReceiptNote grn = GoodsReceiptNote.builder()
                .grnNumber("GRN-TEST-002")
                .purchaseOrder(purchaseOrder)
                .items(List.of(grnItem))
                .build();
        goodsReceiptNoteRepository.save(grn);

        // Create Invoice with identical quantities and prices
        InvoiceItem invoiceItem = InvoiceItem.builder()
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
                .items(List.of(invoiceItem))
                .purchaseOrderId(purchaseOrder.getId())
                .build();

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
    @DisplayName("Get purchase order should return all items and details")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
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
                .andExpect(jsonPath("$.data.po_number").value("PO-TEST-003"))
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
