package com.oct.invoicesystem.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdSecretConfigValidator {

    private final Environment environment;

    private static final List<String> REQUIRED_SECRET_PROPERTIES = List.of(
            "jwt.private-key",          // RSA-2048 PKCS#8 Base64 private key (signs tokens)
            "jwt.public-key",           // RSA-2048 X.509 Base64 public key (verifies tokens)
            "encryption.key",
            "spring.datasource.password",
            "minio.access-key",
            "minio.secret-key",
            "spring.mail.username",
            "spring.mail.password",
            "server.ssl.key-store",     // Path to TLS keystore (PKCS12)
            "server.ssl.key-store-password"
    );

    @PostConstruct
    public void validateSecrets() {
        for (String property : REQUIRED_SECRET_PROPERTIES) {
            String value = environment.getProperty(property);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing required production secret: " + property);
            }
        }
    }
}
