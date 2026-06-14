package com.oct.invoicesystem.domain.access.model;

/**
 * Lifecycle status of a self-service access request (P11-17 / REQ-23 item 3).
 * A request is created {@link #PENDING}; an ADMIN review moves it to {@link #APPROVED}
 * (the requested role is added to the requester) or {@link #REJECTED}.
 */
public enum AccessRequestStatus {
    PENDING("En attente", "Pending"),
    APPROVED("Approuvée", "Approved"),
    REJECTED("Rejetée", "Rejected");

    private final String frenchLabel;
    private final String englishLabel;

    AccessRequestStatus(String frenchLabel, String englishLabel) {
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
