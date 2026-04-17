package com.oct.invoicesystem.domain.payment.service;

import com.oct.invoicesystem.domain.payment.dto.RemittanceAdviceDTO;
import java.util.UUID;

public interface RemittanceAdviceService {

    /**
     * Generate remittance advice PDF for a payment and store it in MinIO.
     * Stores reference in remittance_advice table.
     *
     * @param paymentId Payment ID to generate advice for
     * @param userId User ID generating the advice
     * @return RemittanceAdviceDTO with generated data
     */
    RemittanceAdviceDTO generateRemittanceAdvice(UUID paymentId, UUID userId);

    /**
     * Get remittance advice by payment ID
     *
     * @param paymentId Payment ID
     * @return RemittanceAdviceDTO if exists
     */
    RemittanceAdviceDTO getByPaymentId(UUID paymentId);

    /**
     * Get pre-signed download URL for remittance PDF
     *
     * @param paymentId Payment ID
     * @return Pre-signed URL for MinIO object
     */
    String getDownloadUrl(UUID paymentId);
}
