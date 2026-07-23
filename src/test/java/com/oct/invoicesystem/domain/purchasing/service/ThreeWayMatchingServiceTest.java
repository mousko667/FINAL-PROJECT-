package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptNote;
import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptItem;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderItem;
import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import com.oct.invoicesystem.domain.purchasing.repository.MatchingConfigRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

@DisplayName("ThreeWayMatchingService Unit Tests")
class ThreeWayMatchingServiceTest {

    @Mock
    private ThreeWayMatchingResultRepository matchingResultRepository;

    @Mock
    private MatchingConfigRepository matchingConfigRepository;

    /** AUDIT-034: the result is now persisted through its own REQUIRES_NEW transaction. */
    @Mock
    private com.oct.invoicesystem.domain.purchasing.service.MatchingResultRecorder matchingResultRecorder;

    @Spy
    private MatchingComparator matchingComparator = new MatchingComparator();

    @InjectMocks
    private ThreeWayMatchingService threeWayMatchingService;

    private MatchingConfig activeConfig;
    private Invoice invoice;
    private PurchaseOrder purchaseOrder;
    private GoodsReceiptNote goodsReceiptNote;
    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        activeConfig = MatchingConfig.builder()
                .id(UUID.randomUUID())
                .tolerancePercentage(new BigDecimal("2.00"))
                .toleranceAmount(new BigDecimal("10.00"))
                .requireGrn(false)
                .isActive(true)
                .updatedBy(mock(User.class))
                .updatedAt(Instant.now())
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .build();

        when(matchingConfigRepository.findByIsActiveTrue()).thenReturn(Optional.of(activeConfig));
        
        // Mock the recorder to return the argument passed to it (AUDIT-034: match() persists via
        // MatchingResultRecorder so a MISMATCH rollback cannot erase the audit trail).
        org.mockito.Mockito.lenient()
                .when(matchingResultRecorder.record(any(com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(matchingResultRepository.save(any(com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Should successfully match invoice with PO when quantities and prices are exactly equal")
    void testPerfectMatch() {
        // Setup invoice
        InvoiceItem invoiceItem = InvoiceItem.builder()
                .id(UUID.randomUUID())
                .description("Widget A")
                .quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("5000.00"))
                .build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00001")
                .amount(new BigDecimal("5000.00"))
                .currency("XAF")
                .items(List.of(invoiceItem))
                .build();

        // Setup PO
        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .id(UUID.randomUUID())
                .itemDescription("Widget A")
                .quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00"))
                .build();

        purchaseOrder = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-001")
                .totalAmount(new BigDecimal("5000.00"))
                .items(List.of(poItem))
                .build();

        // Setup and link GRN
        GoodsReceiptItem grnItem = GoodsReceiptItem.builder()
                .id(UUID.randomUUID())
                .purchaseOrderItem(poItem)
                .receivedQuantity(new BigDecimal("100"))
                .build();

        goodsReceiptNote = GoodsReceiptNote.builder()
                .id(UUID.randomUUID())
                .grnNumber("GRN-001")
                .items(List.of(grnItem))
                .build();

        // When
        var result = threeWayMatchingService.match(invoice, purchaseOrder, goodsReceiptNote);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(MatchingStatus.MATCHED);
        assertThat(result.getInvoice()).isEqualTo(invoice);
        assertThat(result.getPurchaseOrder()).isEqualTo(purchaseOrder);
        assertThat(result.getGoodsReceiptNote()).isEqualTo(goodsReceiptNote);
        verify(matchingResultRepository).save(result);
    }

    @Test
    @DisplayName("Should flag as PARTIAL when discrepancy is within tolerance")
    void testPartialMatchWithinTolerance() {
        // Setup invoice with slight variance
        InvoiceItem invoiceItem = InvoiceItem.builder()
                .id(UUID.randomUUID())
                .description("Widget B")
                .quantity(new BigDecimal("101")) // 1% above PO
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("5050.00"))
                .build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00002")
                .amount(new BigDecimal("5050.00"))
                .currency("XAF")
                .items(List.of(invoiceItem))
                .build();

        // Setup PO
        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .id(UUID.randomUUID())
                .itemDescription("Widget B")
                .quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00"))
                .build();

        purchaseOrder = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-002")
                .totalAmount(new BigDecimal("5000.00"))
                .items(List.of(poItem))
                .build();

        // When
        var result = threeWayMatchingService.match(invoice, purchaseOrder, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isIn(MatchingStatus.MATCHED, MatchingStatus.PARTIAL);
    }

    @Test
    @DisplayName("Should flag as MISMATCH when discrepancy exceeds tolerance")
    void testMismatchOutsideTolerance() {
        // Setup invoice with large variance
        InvoiceItem invoiceItem = InvoiceItem.builder()
                .id(UUID.randomUUID())
                .description("Widget C")
                .quantity(new BigDecimal("150")) // 50% above PO
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("7500.00"))
                .build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00003")
                .amount(new BigDecimal("7500.00"))
                .currency("XAF")
                .items(List.of(invoiceItem))
                .build();

        // Setup PO
        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .id(UUID.randomUUID())
                .itemDescription("Widget C")
                .quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00"))
                .build();

        purchaseOrder = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-003")
                .totalAmount(new BigDecimal("5000.00"))
                .items(List.of(poItem))
                .build();

        // When
        var result = threeWayMatchingService.match(invoice, purchaseOrder, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(MatchingStatus.MISMATCH);
    }

    @Test
    @DisplayName("Should throw exception when GRN is required but not provided")
    void testGrnRequiredButNotProvided() {
        // Setup config that requires GRN
        MatchingConfig grnRequiredConfig = MatchingConfig.builder()
                .id(UUID.randomUUID())
                .tolerancePercentage(new BigDecimal("2.00"))
                .toleranceAmount(new BigDecimal("10.00"))
                .requireGrn(true)
                .isActive(true)
                .updatedBy(mock(User.class))
                .updatedAt(Instant.now())
                .build();
        
        when(matchingConfigRepository.findByIsActiveTrue()).thenReturn(Optional.of(grnRequiredConfig));

        InvoiceItem invoiceItem = InvoiceItem.builder()
                .id(UUID.randomUUID())
                .description("Widget D")
                .quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("5000.00"))
                .build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00004")
                .amount(new BigDecimal("5000.00"))
                .currency("XAF")
                .items(List.of(invoiceItem))
                .build();

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .id(UUID.randomUUID())
                .itemDescription("Widget D")
                .quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00"))
                .build();

        purchaseOrder = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-004")
                .totalAmount(new BigDecimal("5000.00"))
                .items(List.of(poItem))
                .build();

        // When & Then
        assertThatThrownBy(() -> threeWayMatchingService.match(invoice, purchaseOrder, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("GRN is required");
                
        // Reset to default config for other tests
        when(matchingConfigRepository.findByIsActiveTrue()).thenReturn(Optional.of(activeConfig));
    }

    @Test
    @DisplayName("Should throw exception when invoice has no items")
    void testInvoiceWithNoItems() {
        // Setup invoice with no items
        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00005")
                .amount(BigDecimal.ZERO)
                .currency("XAF")
                .items(new ArrayList<>())
                .build();

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .id(UUID.randomUUID())
                .itemDescription("Widget E")
                .quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00"))
                .build();

        purchaseOrder = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-005")
                .totalAmount(new BigDecimal("5000.00"))
                .items(List.of(poItem))
                .build();

        // When & Then
        assertThatThrownBy(() -> threeWayMatchingService.match(invoice, purchaseOrder, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no line items");
    }

    @Test
    @DisplayName("Should successfully record override for MISMATCH result")
    void testRecordOverrideForMismatch() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        String overrideReason = "Approved by DAF due to supplier delay";

        var existingResult = com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult.builder()
                .status(MatchingStatus.MISMATCH)
                .invoice(mock(Invoice.class))
                .purchaseOrder(mock(PurchaseOrder.class))
                .build();

        when(matchingResultRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.of(existingResult));
        when(matchingResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        var result = threeWayMatchingService.recordOverride(invoiceId, testUser, overrideReason);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(MatchingStatus.OVERRIDDEN);
        assertThat(result.getOverriddenBy()).isEqualTo(testUser);
        assertThat(result.getOverrideReason()).isEqualTo(overrideReason);
        verify(matchingResultRepository).save(any());
    }

    @Test
    @DisplayName("Should throw exception when override reason is too short")
    void testRecordOverrideWithShortReason() {
        UUID invoiceId = UUID.randomUUID();
        String shortReason = "Too short"; // 9 characters, requires 10

        // When & Then
        assertThatThrownBy(() -> threeWayMatchingService.recordOverride(invoiceId, testUser, shortReason))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("error.matching.override_reason_too_short");
    }

    @Test
    @DisplayName("Should throw exception when trying to override non-MISMATCH result")
    void testRecordOverrideNonMismatchResult() {
        UUID invoiceId = UUID.randomUUID();
        String overrideReason = "Valid override reason with enough characters";

        var existingResult = mock(com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult.class);
        when(existingResult.getStatus()).thenReturn(MatchingStatus.MATCHED); // Not a MISMATCH

        when(matchingResultRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.of(existingResult));

        // When & Then
        assertThatThrownBy(() -> threeWayMatchingService.recordOverride(invoiceId, testUser, overrideReason))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("error.matching.only_mismatch_overridable");
    }

    @Test
    @DisplayName("Should throw exception when matching result not found for override")
    void testRecordOverrideMatchingResultNotFound() {
        UUID invoiceId = UUID.randomUUID();
        String overrideReason = "Valid override reason with enough characters";

        when(matchingResultRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> threeWayMatchingService.recordOverride(invoiceId, testUser, overrideReason))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No matching result found");
    }

    @Test
    @DisplayName("Should throw exception when no active matching config found")
    void testMatchingWithoutActiveConfig() {
        when(matchingConfigRepository.findByIsActiveTrue()).thenReturn(Optional.empty());

        InvoiceItem invoiceItem = InvoiceItem.builder()
                .id(UUID.randomUUID())
                .description("Widget F")
                .quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("5000.00"))
                .build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00006")
                .amount(new BigDecimal("5000.00"))
                .currency("XAF")
                .items(List.of(invoiceItem))
                .build();

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .id(UUID.randomUUID())
                .itemDescription("Widget F")
                .quantity(new BigDecimal("100"))
                .unitPrice(new BigDecimal("50.00"))
                .build();

        purchaseOrder = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-006")
                .totalAmount(new BigDecimal("5000.00"))
                .items(List.of(poItem))
                .build();

        // When & Then
        assertThatThrownBy(() -> threeWayMatchingService.match(invoice, purchaseOrder, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No active matching configuration");
    }
}
