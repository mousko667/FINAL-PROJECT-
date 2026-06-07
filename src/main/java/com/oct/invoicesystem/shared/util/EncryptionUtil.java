package com.oct.invoicesystem.shared.util;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String GCM_PREFIX = "GCM:";
    
    @Value("${encryption.key}")
    private String encryptionKeyString;

    private SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        byte[] key = encryptionKeyString.getBytes(StandardCharsets.UTF_8);
        if (key.length < AES_256_KEY_BYTES) {
            throw new IllegalStateException("encryption.key must contain at least 32 bytes for AES-256");
        }
        byte[] finalKey = new byte[AES_256_KEY_BYTES];
        System.arraycopy(key, 0, finalKey, 0, AES_256_KEY_BYTES);
        secretKey = new SecretKeySpec(finalKey, KEY_ALGORITHM);
    }

    public String encrypt(String valueToEncrypt) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encryptedValue = cipher.doFinal(valueToEncrypt.getBytes(StandardCharsets.UTF_8));
            return GCM_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(encryptedValue);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting value", e);
        }
    }

    public String decrypt(String encryptedValue) {
        try {
            if (encryptedValue != null && encryptedValue.startsWith(GCM_PREFIX)) {
                String[] parts = encryptedValue.split(":", 3);
                byte[] iv = Base64.getDecoder().decode(parts[1]);
                byte[] cipherText = Base64.getDecoder().decode(parts[2]);
                Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
                return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
            }

            Cipher cipher = Cipher.getInstance(KEY_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedValue = Base64.getDecoder().decode(encryptedValue);
            byte[] decryptedValue = cipher.doFinal(decodedValue);
            return new String(decryptedValue, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting value", e);
        }
    }
}
