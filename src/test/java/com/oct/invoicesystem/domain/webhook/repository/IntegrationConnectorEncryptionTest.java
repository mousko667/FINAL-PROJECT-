package com.oct.invoicesystem.domain.webhook.repository;

import com.oct.invoicesystem.domain.webhook.model.IntegrationConnector;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MAJEUR-13 / PROB-099: {@link IntegrationConnector#getConfig()} may hold ERP/BANKING/DMS
 * connector settings, including credentials or API keys, and must not be stored in clear —
 * mirrors the existing {@code Supplier.bankDetails} encryption-at-rest pattern.
 *
 * <p>Full {@code @SpringBootTest} context (rather than a sliced {@code @DataJpaTest}) is used so
 * the {@code EncryptionAttributeConverter}'s {@code EncryptionUtil} dependency is guaranteed to be
 * wired via the real application context, matching how the converter is exercised in production.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IntegrationConnectorEncryptionTest {

    private static final String PLAINTEXT_CONFIG = "secret-token-12345";

    @Autowired
    private IntegrationConnectorRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void configIsEncryptedAtRestButTransparentThroughTheRepository() {
        IntegrationConnector connector = IntegrationConnector.builder()
                .name("Encryption Test Connector")
                .type("ERP")
                .config(PLAINTEXT_CONFIG)
                .build();

        IntegrationConnector saved = repository.save(connector);
        entityManager.flush();
        entityManager.clear();

        // Transparent decryption on read via the repository / entity manager.
        IntegrationConnector reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getConfig()).isEqualTo(PLAINTEXT_CONFIG);

        // The raw stored column must NOT contain the plaintext — it must be ciphertext.
        Object rawValue = entityManager.createNativeQuery(
                        "SELECT config FROM integration_connectors WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();

        assertThat(rawValue).isInstanceOf(String.class);
        assertThat((String) rawValue).isNotEqualTo(PLAINTEXT_CONFIG);
        assertThat((String) rawValue).startsWith("GCM:");
    }
}
