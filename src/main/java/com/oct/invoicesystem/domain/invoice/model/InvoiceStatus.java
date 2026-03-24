package com.oct.invoicesystem.domain.invoice.model;

public enum InvoiceStatus {
    BROUILLON("Brouillon", "Draft"),
    SOUMIS("Soumis", "Submitted"),
    EN_VALIDATION_N1("En validation N1", "Under review L1"),
    EN_VALIDATION_N2("En validation N2", "Under review L2"),
    VALIDE("Valide", "Validated"),
    BON_A_PAYER("Bon a payer", "Approved for payment"),
    PAYE("Paye", "Paid"),
    ARCHIVE("Archive", "Archived"),
    REJETE("Rejete", "Rejected");

    private final String frenchLabel;
    private final String englishLabel;

    InvoiceStatus(String frenchLabel, String englishLabel) {
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
