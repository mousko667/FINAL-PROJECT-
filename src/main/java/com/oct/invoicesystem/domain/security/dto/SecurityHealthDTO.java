package com.oct.invoicesystem.domain.security.dto;

/**
 * Security-health snapshot (P11-53 / REQ-24, partial scope — 2 of 8 items).
 * Surfaces the four operational security signals the system can compute today:
 * encryption coverage, MFA adoption, login-failure trend, and webhook delivery success.
 */
public record SecurityHealthDTO(
        boolean atRestEncryptionEnabled,   // bank details encrypted at rest via @Convert (AES)
        long encryptedBankDetailRecords,   // count of invoices carrying encrypted bank details
        long totalActiveUsers,
        long mfaEnabledUsers,
        double mfaAdoptionPercent,         // mfaEnabledUsers / totalActiveUsers * 100
        long lockedAccounts,               // accounts currently locked out (login-failure signal)
        long totalFailedLoginAttempts,     // sum of outstanding failed-login counters
        double webhookDeliverySuccessRate  // 0..1 over the recent window
) {}
