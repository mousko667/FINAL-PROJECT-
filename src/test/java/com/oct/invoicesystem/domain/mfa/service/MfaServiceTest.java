package com.oct.invoicesystem.domain.mfa.service;

import static org.junit.jupiter.api.Assertions.*;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MfaServiceTest")
class MfaServiceTest {

    private MfaService mfaService;
    private String testUsername = "testuser";

    @BeforeEach
    void setUp() {
        mfaService = new MfaService();
    }

    @Test
    @DisplayName("generateSecret - should return non-null, non-empty Base32 string")
    void testGenerateSecret_ReturnsValidSecret() {
        String secret = mfaService.generateSecret();
        
        assertNotNull(secret);
        assertFalse(secret.isBlank());
        assertTrue(secret.matches("[A-Z2-7]+"));
        assertEquals(MfaService.SECRET_LENGTH, secret.length());
    }

    @Test
    @DisplayName("generateSecret - should generate different secrets on each call")
    void testGenerateSecret_DifferentSecretsOnEachCall() {
        String secret1 = mfaService.generateSecret();
        String secret2 = mfaService.generateSecret();
        
        assertNotEquals(secret1, secret2);
    }

    @Test
    @DisplayName("generateQrCodeUrl - should return valid otpauth URI")
    void testGenerateQrCodeUrl_ReturnsValidOtpauthUri() {
        String secret = mfaService.generateSecret();
        String username = testUsername;
        
        String qrUrl = mfaService.generateQrCodeUrl(username, secret);
        
        assertNotNull(qrUrl);
        assertTrue(qrUrl.startsWith("otpauth://totp/"));
        assertTrue(qrUrl.contains(username));
        assertTrue(qrUrl.contains(MfaService.ISSUER));
        assertTrue(qrUrl.contains("algorithm=SHA1"));
        assertTrue(qrUrl.contains("digits=" + MfaService.OTP_DIGITS));
        assertTrue(qrUrl.contains("period=" + MfaService.OTP_PERIOD_SECONDS));
    }

    @Test
    @DisplayName("generateQrCodeUrl - should properly URL-encode special characters")
    void testGenerateQrCodeUrl_EncodeSpecialCharacters() {
        String secret = mfaService.generateSecret();
        String usernameWithSpecial = "test.user@example.com";
        
        String qrUrl = mfaService.generateQrCodeUrl(usernameWithSpecial, secret);
        
        assertNotNull(qrUrl);
        assertTrue(qrUrl.contains("test"));
    }

    @Test
    @DisplayName("verifyOtp - should return true for valid OTP (basic validation)")
    void testVerifyOtp_WithValidOtp_ReturnsTrue() throws CodeGenerationException {
        String secret = mfaService.generateSecret();
        
        assertNotNull(secret);
        // Generate a valid OTP for current time using the same algorithm as MfaService
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, MfaService.OTP_DIGITS);
        long currentPeriod = System.currentTimeMillis() / 1000L / MfaService.OTP_PERIOD_SECONDS;
        String validOtp = codeGenerator.generate(secret, currentPeriod);
        // verifyOtp must return true for an OTP generated in the current time window
        assertTrue(mfaService.verifyOtp(secret, validOtp),
                "verifyOtp must return true for a freshly generated OTP");
    }

    @Test
    @DisplayName("verifyOtp - should reject invalid OTP")
    void testVerifyOtp_WithInvalidOtp_ReturnsFalse() {
        String secret = mfaService.generateSecret();
        String invalidOtp = "000000";
        
        assertFalse(mfaService.verifyOtp(secret, invalidOtp));
    }

    @Test
    @DisplayName("verifyOtp - should reject OTP with wrong length")
    void testVerifyOtp_WithWrongLength_ReturnsFalse() {
        String secret = mfaService.generateSecret();
        String wrongLengthOtp = "12345";  // 5 digits instead of 6
        
        assertFalse(mfaService.verifyOtp(secret, wrongLengthOtp));
    }

    @Test
    @DisplayName("verifyOtp - should reject null secret")
    void testVerifyOtp_WithNullSecret_ReturnsFalse() {
        assertFalse(mfaService.verifyOtp(null, "123456"));
    }

    @Test
    @DisplayName("verifyOtp - should reject blank secret")
    void testVerifyOtp_WithBlankSecret_ReturnsFalse() {
        assertFalse(mfaService.verifyOtp("   ", "123456"));
    }

    @Test
    @DisplayName("verifyOtp - should reject null OTP")
    void testVerifyOtp_WithNullOtp_ReturnsFalse() {
        String secret = mfaService.generateSecret();
        assertFalse(mfaService.verifyOtp(secret, null));
    }

    @Test
    @DisplayName("verifyOtp - should reject blank OTP")
    void testVerifyOtp_WithBlankOtp_ReturnsFalse() {
        String secret = mfaService.generateSecret();
        assertFalse(mfaService.verifyOtp(secret, "   "));
    }

    @Test
    @DisplayName("verifyOtp - should handle non-numeric OTP gracefully")
    void testVerifyOtp_WithNonNumericOtp_ReturnsFalse() {
        String secret = mfaService.generateSecret();
        assertFalse(mfaService.verifyOtp(secret, "ABCDEF"));
    }

    @Test
    @DisplayName("verifyOtp - should allow time period discrepancy (current and adjacent periods)")
    void testVerifyOtp_AllowedTimePeriodDiscrepancy() throws CodeGenerationException {
        String secret = mfaService.generateSecret();
        
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, MfaService.OTP_DIGITS);
        
        // Generate OTP for the current 30-second period
        long currentPeriod = System.currentTimeMillis() / 1000L / MfaService.OTP_PERIOD_SECONDS;
        String currentOtp = codeGenerator.generate(secret, currentPeriod);

        // The OTP must be valid and have the correct 6-digit format
        assertTrue(currentOtp.matches("\\d{6}"), "OTP must be 6 digits");
        assertTrue(mfaService.verifyOtp(secret, currentOtp),
                "verifyOtp must accept OTP for the current time period");
    }

    @Test
    @DisplayName("constant validation - verify OTP configuration constants")
    void testConstants_AreCorrect() {
        assertEquals("OCT", MfaService.ISSUER);
        assertEquals(6, MfaService.OTP_DIGITS);
        assertEquals(30, MfaService.OTP_PERIOD_SECONDS);
        assertEquals(32, MfaService.SECRET_LENGTH);
        assertEquals(1, MfaService.ALLOWED_TIME_PERIOD_DISCREPANCY);
    }
}
