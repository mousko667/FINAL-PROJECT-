package com.oct.invoicesystem.domain.invoice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceItemDTO(
        UUID id,
        Integer lineNumber,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
}
