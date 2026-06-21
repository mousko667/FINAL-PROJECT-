package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.purchasing.dto.LineVerdict;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingDetailDTO;
import com.oct.invoicesystem.domain.purchasing.model.*;
import com.oct.invoicesystem.domain.purchasing.repository.*;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingQueryServiceTest {

    @Mock ThreeWayMatchingResultRepository matchingRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock PurchaseOrderRepository poRepo;
    @Mock GoodsReceiptNoteRepository grnRepo;
    @Mock MatchingConfigRepository configRepo;
    @Spy MatchingComparator matchingComparator;
    @InjectMocks MatchingQueryService service;

    @Test
    void getLines_invoiceMissing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceRepo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getLines(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getLines_exactLine_verdictMatched() {
        UUID invId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();

        Invoice inv = new Invoice();
        inv.setReferenceNumber("INV-1");
        inv.setSupplierName("ACME");
        inv.setPurchaseOrderId(poId);
        InvoiceItem ii = new InvoiceItem();
        ii.setDescription("Widget"); ii.setQuantity(new BigDecimal("10")); ii.setUnitPrice(new BigDecimal("5.00"));
        inv.setItems(List.of(ii));

        PurchaseOrder po = PurchaseOrder.builder().poNumber("PO-1").build();
        PurchaseOrderItem pi = new PurchaseOrderItem();
        pi.setItemDescription("Widget"); pi.setQuantity(new BigDecimal("10")); pi.setUnitPrice(new BigDecimal("5.00"));
        po.setItems(List.of(pi));

        MatchingConfig config = MatchingConfig.builder()
                .tolerancePercentage(new BigDecimal("2.00")).toleranceAmount(BigDecimal.ZERO).build();

        when(invoiceRepo.findById(invId)).thenReturn(Optional.of(inv));
        when(poRepo.findById(poId)).thenReturn(Optional.of(po));
        when(grnRepo.findByPurchaseOrderId(poId)).thenReturn(List.of());
        when(configRepo.findByIsActiveTrue()).thenReturn(Optional.of(config));
        when(matchingRepo.findByInvoiceId(invId)).thenReturn(Optional.empty());

        MatchingDetailDTO dto = service.getLines(invId);
        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.lines().get(0).verdict()).isEqualTo(LineVerdict.MATCHED);
    }

    @Test
    void getLines_invoiceLineWithoutPo_verdictMissingInPo() {
        UUID invId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();

        Invoice inv = new Invoice();
        inv.setReferenceNumber("INV-2"); inv.setSupplierName("ACME"); inv.setPurchaseOrderId(poId);
        InvoiceItem ii = new InvoiceItem();
        ii.setDescription("Ghost"); ii.setQuantity(new BigDecimal("1")); ii.setUnitPrice(new BigDecimal("9.00"));
        inv.setItems(List.of(ii));

        PurchaseOrder po = PurchaseOrder.builder().poNumber("PO-2").build();
        po.setItems(List.of());
        MatchingConfig config = MatchingConfig.builder()
                .tolerancePercentage(new BigDecimal("2.00")).toleranceAmount(BigDecimal.ZERO).build();

        when(invoiceRepo.findById(invId)).thenReturn(Optional.of(inv));
        when(poRepo.findById(poId)).thenReturn(Optional.of(po));
        when(grnRepo.findByPurchaseOrderId(poId)).thenReturn(List.of());
        when(configRepo.findByIsActiveTrue()).thenReturn(Optional.of(config));
        when(matchingRepo.findByInvoiceId(invId)).thenReturn(Optional.empty());

        MatchingDetailDTO dto = service.getLines(invId);
        assertThat(dto.lines().get(0).verdict()).isEqualTo(LineVerdict.MISSING_IN_PO);
    }
}
