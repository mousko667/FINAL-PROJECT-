package com.oct.invoicesystem.domain.supplier.dto;

import java.util.UUID;

/**
 * Minimal supplier reference for populating report/filter dropdowns: id + display name only.
 *
 * <p>Deliberately excludes every operational field of {@code SupplierResponse} (bank details,
 * contacts, status, tax id…). This lets roles that may read supplier <em>reports</em> but not the
 * supplier <em>master data</em> — notably {@code ROLE_DAF} — obtain the labels they need to drive a
 * selector without reopening the master-data surface closed to them in audit wave V2-A.</p>
 */
public record SupplierOptionDTO(UUID id, String companyName) {}
