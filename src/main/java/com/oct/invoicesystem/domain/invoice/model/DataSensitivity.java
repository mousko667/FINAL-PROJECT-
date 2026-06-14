package com.oct.invoicesystem.domain.invoice.model;

/**
 * Data-sensitivity classification of a financial record (P11-15 / REQ-23 item 1).
 * Drives how confidential an invoice's data is considered. Stored as a string on the invoice;
 * defaults to {@link #INTERNAL} (every invoice is at least internal company data).
 */
public enum DataSensitivity {
    PUBLIC("Public", "Public"),
    INTERNAL("Interne", "Internal"),
    CONFIDENTIAL("Confidentiel", "Confidential");

    private final String frenchLabel;
    private final String englishLabel;

    DataSensitivity(String frenchLabel, String englishLabel) {
        this.frenchLabel = frenchLabel;
        this.englishLabel = englishLabel;
    }

    public String getFrenchLabel() {
        return frenchLabel;
    }

    public String getEnglishLabel() {
        return englishLabel;
    }
}
