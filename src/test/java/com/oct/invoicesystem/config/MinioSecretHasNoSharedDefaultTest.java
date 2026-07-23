package com.oct.invoicesystem.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUDIT-012 — {@code MINIO_SECRET_KEY} must have no default outside the dev profile.
 *
 * <p>The shared block carried {@code ${MINIO_SECRET_KEY:dev-minio-secret-change-me}}. Since that
 * block applies to <em>every</em> profile, a production deployment that forgot the variable would
 * start the document store with a publicly known secret instead of failing loudly — unlike
 * {@code JWT_PRIVATE_KEY} and {@code ENCRYPTION_KEY}, which are deliberately default-less.</p>
 *
 * <p>This reads the packaged {@code application.yaml} rather than the resolved Spring property,
 * because the defect is the presence of a default <em>in the shared block</em>: any test running
 * under a profile that sets the value would resolve it fine and prove nothing.</p>
 */
class MinioSecretHasNoSharedDefaultTest {

    @Test
    void sharedBlock_declaresMinioSecretWithoutFallback() throws IOException {
        String yaml = readApplicationYaml();
        // Everything before the first "---" is the profile-independent block.
        String sharedBlock = yaml.split("(?m)^---\\s*$")[0];

        assertThat(sharedBlock)
                .as("the shared block must require MINIO_SECRET_KEY, so a missing value fails startup")
                .contains("secret-key: ${MINIO_SECRET_KEY}")
                .doesNotContain("${MINIO_SECRET_KEY:");
    }

    @Test
    void devProfile_keepsTheLocalConvenienceDefault() throws IOException {
        // Counter-proof: the secret was not simply made mandatory everywhere — local development
        // still starts without setting an environment variable.
        String yaml = readApplicationYaml();
        String[] blocks = yaml.split("(?m)^---\\s*$");
        String devBlock = java.util.Arrays.stream(blocks)
                .filter(b -> b.contains("on-profile: dev"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("dev profile block not found in application.yaml"));

        assertThat(devBlock).contains("${MINIO_SECRET_KEY:");
    }

    private String readApplicationYaml() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            assertThat(in).as("application.yaml must be on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
