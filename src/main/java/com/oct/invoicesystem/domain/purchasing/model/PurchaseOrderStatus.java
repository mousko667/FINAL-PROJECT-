package com.oct.invoicesystem.domain.purchasing.model;

/**
 * Enumeration of Purchase Order statuses in the procurement workflow.
 * - OPEN: PO is available for matching with GRN and invoices
 * - CLOSED: All invoices associated with this PO have been paid
 * - CANCELLED: PO is no longer valid and cannot be referenced on new invoices
 */
public enum PurchaseOrderStatus {
    OPEN,
    CLOSED,
    CANCELLED
}
