package com.oct.invoicesystem.domain.ocr.service;

import com.oct.invoicesystem.domain.ocr.dto.OcrExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OcrServiceTest {

    private OcrService ocrService;

    @BeforeEach
    void setUp() {
        ocrService = new OcrService();
    }

    @Test
    @DisplayName("parseFields: extracts invoice number from standard EN label")
    void parseFields_extractsInvoiceNumber() {
        String text = "Invoice No: FAC-2026-00041\nDate: 15/03/2026\nTotal: 450 000,00";
        OcrExtractionResult result = ocrService.parseFields(text, true);
        assertThat(result.getInvoiceNumber()).isEqualTo("FAC-2026-00041");
    }

    @Test
    @DisplayName("parseFields: extracts invoice number from French label")
    void parseFields_extractsInvoiceNumber_frenchLabel() {
        String text = "Facture N°: FACT-2026-0012\nDate: 01/04/2026";
        OcrExtractionResult result = ocrService.parseFields(text, true);
        assertThat(result.getInvoiceNumber()).isEqualTo("FACT-2026-0012");
    }

    @Test
    @DisplayName("parseFields: extracts date in dd/MM/yyyy format")
    void parseFields_extractsDate() {
        String text = "Invoice No: INV-001\nDate: 15/03/2026\nTotal: 100.00";
        OcrExtractionResult result = ocrService.parseFields(text, true);
        assertThat(result.getInvoiceDate()).isEqualTo("15/03/2026");
    }

    @Test
    @DisplayName("parseFields: extracts total amount with space thousands separator")
    void parseFields_extractsTotalAmount() {
        String text = "Invoice No: INV-001\nTotal Amount: 450 000.00";
        OcrExtractionResult result = ocrService.parseFields(text, true);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("450000.00"));
    }

    @Test
    @DisplayName("parseFields: extracts PO reference")
    void parseFields_extractsPoReference() {
        String text = "P.O. Number: PO-2026-0041\nInvoice No: INV-001";
        OcrExtractionResult result = ocrService.parseFields(text, true);
        assertThat(result.getPoReference()).isEqualTo("PO-2026-0041");
    }

    @Test
    @DisplayName("parseFields: extracts supplier NIF tax ID")
    void parseFields_extractsSupplierId() {
        String text = "NIF: GA-123456789\nInvoice No: INV-001";
        OcrExtractionResult result = ocrService.parseFields(text, true);
        assertThat(result.getSupplierId()).isEqualTo("GA-123456789");
    }

    @Test
    @DisplayName("parseFields: returns null fields on empty text without throwing")
    void parseFields_emptyText_returnsNullFields() {
        OcrExtractionResult result = ocrService.parseFields("", false);
        assertThat(result.getInvoiceNumber()).isNull();
        assertThat(result.getTotalAmount()).isNull();
        assertThat(result.getLineItems()).isEmpty();
        assertThat(result.getRawText()).isEmpty();
    }

    @Test
    @DisplayName("parseFields: digitalPdf flag is preserved in result")
    void parseFields_digitalPdfFlagPreserved() {
        OcrExtractionResult digital = ocrService.parseFields("Invoice No: INV-1", true);
        OcrExtractionResult scanned = ocrService.parseFields("Invoice No: INV-1", false);
        assertThat(digital.isDigitalPdf()).isTrue();
        assertThat(scanned.isDigitalPdf()).isFalse();
    }

    @Test
    @DisplayName("parseFields: rawText is passed through unchanged")
    void parseFields_rawTextPreserved() {
        String raw = "Invoice No: INV-001\nTotal: 100.00";
        OcrExtractionResult result = ocrService.parseFields(raw, true);
        assertThat(result.getRawText()).isEqualTo(raw);
    }
}
