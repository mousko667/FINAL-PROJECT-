package com.oct.invoicesystem.domain.security.service;

import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.security.dto.SecurityHealthDTO;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.webhook.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Computes the security-health snapshot (P11-53 / REQ-24, partial scope — 2 of 8 items).
 * Aggregates four signals the system can compute today; the remaining REQ-24 items
 * (backup status, privacy-policy tracking, incident reporting, SOX/IFRS checklist,
 * compliance calendar) are deferred — see KNOWN_ISSUES_REGISTRY.
 */
@Service
@RequiredArgsConstructor
public class SecurityHealthService {

    private static final int WEBHOOK_WINDOW_DAYS = 7;

    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;

    @Transactional(readOnly = true)
    public SecurityHealthDTO getSecurityHealth() {
        long activeUsers = userRepository.countByActiveTrue();
        long mfaUsers = userRepository.countByActiveTrueAndMfaEnabledTrue();
        double mfaAdoption = activeUsers > 0 ? (mfaUsers * 100.0) / activeUsers : 0.0;

        long locked = userRepository.countLockedAccounts(Instant.now());
        long failedAttempts = userRepository.sumFailedLoginAttempts();
        long encryptedBankDetails = invoiceRepository.countBySupplierBankDetailsIsNotNull();

        Instant since = Instant.now().minus(WEBHOOK_WINDOW_DAYS, ChronoUnit.DAYS);
        long totalDeliveries = webhookDeliveryRepository.countByCreatedAtAfter(since);
        long successfulDeliveries = webhookDeliveryRepository.countByCreatedAtAfterAndSuccessTrue(since);
        double webhookSuccessRate = totalDeliveries > 0
                ? (double) successfulDeliveries / totalDeliveries
                : 0.0;

        return new SecurityHealthDTO(
                true, // bank details are encrypted at rest via @Convert (AES) by design
                encryptedBankDetails,
                activeUsers,
                mfaUsers,
                mfaAdoption,
                locked,
                failedAttempts,
                webhookSuccessRate
        );
    }
}
