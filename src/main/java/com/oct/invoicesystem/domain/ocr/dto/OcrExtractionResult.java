package com.oct.invoicesystem.domain.ocr.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result returned by OcrService after extracting fields from an uploaded invoice file.
 * The supplier reviews and corrects these fields before final submission.
 */
@Getter
@Builder
public class OcrExtractionResult {

    private String invoiceNumber;
    private String invoiceDate;
    private BigDecimal totalAmount;
    private String supplierId;
    private String poReference;
    private List<OcrLineItem> lineItems;

    /** Raw extracted text — useful for supplier review and debugging. */
    private String rawText;

    /** true if text was extracted from a digital PDF layer; false if OCR was used on an image. */
    private boolean digitalPdf;

    @Getter
    @Builder
    public static class OcrLineItem {
        private String description;
        private String quantity;
        private String unitPrice;
    }
}
