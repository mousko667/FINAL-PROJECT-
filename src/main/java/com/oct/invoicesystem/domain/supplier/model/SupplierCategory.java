package com.oct.invoicesystem.domain.supplier.model;

/**
 * Spend-type segmentation of a supplier (B5). Categorises a supplier by the nature of what it
 * provides, used for directory filtering and supplier analytics.
 */
public enum SupplierCategory {
    GOODS,
    SERVICES,
    WORKS,
    CONSULTING
}
