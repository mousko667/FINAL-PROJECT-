package com.oct.invoicesystem.domain.payment.service;

import com.oct.invoicesystem.domain.payment.dto.BatchPaymentRequest;
import com.oct.invoicesystem.domain.payment.dto.BatchPaymentResultDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentRequest;
import com.oct.invoicesystem.shared.export.TabularExportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {
    PaymentDTO recordPayment(UUID invoiceId, PaymentRequest request, UUID userId);
    BatchPaymentResultDTO recordBatchPayment(BatchPaymentRequest request, UUID userId);
    PaymentDTO getPaymentByInvoiceId(UUID invoiceId);
    Page<PaymentDTO> listPayments(String departmentCode, Pageable pageable);
    byte[] exportPayments(String departmentCode, TabularExportService.Format format);

    /**
     * Marque un paiement planifie (SCHEDULED) comme execute (PROCESSED) et finalise :
     * generation de l'avis de paiement, publication de l'evenement, transitions PAYE puis ARCHIVE.
     *
     * @throws com.oct.invoicesystem.shared.exception.ResourceNotFoundException si le paiement est introuvable
     * @throws com.oct.invoicesystem.shared.exception.WorkflowException si le paiement n'est pas SCHEDULED
     */
    PaymentDTO processPayment(UUID paymentId, UUID userId);
}
