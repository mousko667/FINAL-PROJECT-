package com.oct.invoicesystem.domain.retention.dto;

import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import jakarta.validation.constraints.NotNull;

/** Body for setting a document's retention disposition. */
public record RetentionDispositionRequest(
        @NotNull RetentionDisposition disposition
) {}
