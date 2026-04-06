package com.oct.invoicesystem.domain.payment.service;

import com.oct.invoicesystem.domain.payment.dto.PaymentDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {
    PaymentDTO recordPayment(UUID invoiceId, PaymentRequest request, UUID userId);
    PaymentDTO getPaymentByInvoiceId(UUID invoiceId);
    Page<PaymentDTO> listPayments(String departmentCode, Pageable pageable);
}
