package com.oct.invoicesystem.domain.mfa.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Generates and validates TOTP material used by the MFA flow.
 */
@Service
@Slf4j
public class MfaService {

    public static final String ISSUER = "OCT";
    public static final int OTP_DIGITS = 6;
    public static final int OTP_PERIOD_SECONDS = 30;
    public static final int SECRET_LENGTH = 32;
    public static final int ALLOWED_TIME_PERIOD_DISCREPANCY = 1;

    private final SecretGenerator secretGenerator;
    private final CodeVerifier codeVerifier;

    public MfaService() {
        this.secretGenerator = new DefaultSecretGenerator(SECRET_LENGTH);
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, OTP_DIGITS);
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        verifier.setTimePeriod(OTP_PERIOD_SECONDS);
        verifier.setAllowedTimePeriodDiscrepancy(ALLOWED_TIME_PERIOD_DISCREPANCY);
        this.codeVerifier = verifier;
    }

    /**
     * Generates a new TOTP shared secret for a user.
     *
     * @return the Base32-encoded secret
     */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Builds the otpauth URI used by authenticator apps to register MFA.
     *
     * @param username the username shown in the authenticator app
     * @param secret the shared secret
     * @return the otpauth URI for the QR code
     */
    public String generateQrCodeUrl(String username, String secret) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(OTP_DIGITS)
                .period(OTP_PERIOD_SECONDS)
                .build();
        return data.getUri();
    }

    /**
     * Verifies a submitted one-time password against a user's shared secret.
     *
     * @param secret the shared secret
     * @param otp the submitted one-time password
     * @return true when the OTP is valid for the current time window
     */
    public boolean verifyOtp(String secret, String otp) {
        if (secret == null || secret.isBlank() || otp == null || otp.isBlank()) {
            return false;
        }

        try {
            return codeVerifier.isValidCode(secret, otp);
        } catch (IllegalArgumentException ex) {
            log.debug("Rejected malformed OTP during MFA verification");
            return false;
        }
    }
}
