package com.oct.invoicesystem.domain.retention.dto;

import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;

import java.time.Instant;
import java.util.UUID;

/** A document past its retention horizon, for ADMIN disposition (M10 #10 refinement). */
public record RetentionPendingDocumentDTO(
        UUID id,
        UUID invoiceId,
        String originalFilename,
        Instant uploadedAt,
        RetentionDisposition retentionDisposition
) {}
