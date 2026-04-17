package com.oct.invoicesystem.domain.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RemittanceAdviceDTO(
    UUID id,
    UUID paymentId,
    String pdfObjectKey,
    Instant generatedAt,
    UUID generatedBy
) {}
