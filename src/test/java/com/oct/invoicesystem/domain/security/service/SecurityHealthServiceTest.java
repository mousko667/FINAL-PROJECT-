package com.oct.invoicesystem.domain.security.service;

import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.security.dto.SecurityHealthDTO;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.webhook.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityHealthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private WebhookDeliveryRepository webhookDeliveryRepository;

    @InjectMocks private SecurityHealthService service;

    @Test
    void getSecurityHealth_computesAdoptionAndRates() {
        when(userRepository.countByActiveTrue()).thenReturn(10L);
        when(userRepository.countByActiveTrueAndMfaEnabledTrue()).thenReturn(4L);
        when(userRepository.countLockedAccounts(any())).thenReturn(2L);
        when(userRepository.sumFailedLoginAttempts()).thenReturn(7L);
        when(invoiceRepository.countBySupplierBankDetailsIsNotNull()).thenReturn(15L);
        when(webhookDeliveryRepository.countByCreatedAtAfter(any())).thenReturn(20L);
        when(webhookDeliveryRepository.countByCreatedAtAfterAndSuccessTrue(any())).thenReturn(18L);

        SecurityHealthDTO result = service.getSecurityHealth();

        assertTrue(result.atRestEncryptionEnabled());
        assertEquals(15L, result.encryptedBankDetailRecords());
        assertEquals(10L, result.totalActiveUsers());
        assertEquals(4L, result.mfaEnabledUsers());
        assertEquals(40.0, result.mfaAdoptionPercent());     // 4/10 * 100
        assertEquals(2L, result.lockedAccounts());
        assertEquals(7L, result.totalFailedLoginAttempts());
        assertEquals(0.9, result.webhookDeliverySuccessRate()); // 18/20
    }

    @Test
    void getSecurityHealth_noUsers_zeroAdoptionNoDivideByZero() {
        when(userRepository.countByActiveTrue()).thenReturn(0L);
        when(userRepository.countByActiveTrueAndMfaEnabledTrue()).thenReturn(0L);
        when(userRepository.countLockedAccounts(any())).thenReturn(0L);
        when(userRepository.sumFailedLoginAttempts()).thenReturn(0L);
        when(invoiceRepository.countBySupplierBankDetailsIsNotNull()).thenReturn(0L);
        when(webhookDeliveryRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        when(webhookDeliveryRepository.countByCreatedAtAfterAndSuccessTrue(any())).thenReturn(0L);

        SecurityHealthDTO result = service.getSecurityHealth();

        assertEquals(0.0, result.mfaAdoptionPercent());
        assertEquals(0.0, result.webhookDeliverySuccessRate());
    }
}
