package com.oct.invoicesystem.domain.invoice.statemachine;

/**
 * Events driving the invoice BAP state machine ({@code docs/WORKFLOW.md} §3).
 */
public enum InvoiceEvent {
    SUBMIT,
    ASSIGN_AA,
    ASSIGN_REVIEWER,
    VALIDATE_N1,
    VALIDATE_N2,
    BON_A_PAYER,
    RECORD_PAYMENT,
    REJECT,
    RESUBMIT,
    ARCHIVE
}
