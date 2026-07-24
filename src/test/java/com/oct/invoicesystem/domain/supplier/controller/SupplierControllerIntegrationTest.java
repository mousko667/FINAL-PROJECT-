package com.oct.invoicesystem.domain.supplier.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.supplier.dto.SupplierCreateRequest;
import com.oct.invoicesystem.domain.supplier.model.SupplierCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUDIT-013 / decision D8 — integration coverage of {@code SupplierController}, previously untested.
 * The finding singles it out because it handles <strong>bank details encrypted with AES-256</strong>
 * ({@code Supplier.bankDetails} carries {@code @Convert(EncryptionAttributeConverter.class)}).
 *
 * <p>Two properties are proven end-to-end:</p>
 * <ol>
 *   <li><strong>SoD</strong> — the supplier reference belongs to the AA; ADMIN (no financial access)
 *       and SUPPLIER are refused (403). The proof of a control is its refusal.</li>
 *   <li><strong>Encryption at rest + no plaintext leak</strong> — bank details are accepted on
 *       create but the raw DB column is ciphertext (≠ the submitted plaintext), and the response
 *       DTO never echoes bank details back.</li>
 * </ol>
 *
 * <p>Full context, {@code test} profile (H2, real AES key). {@code @Transactional} → rollback.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("SupplierControllerIntegrationTest")
class SupplierControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    private static final String PLAINTEXT_BANK = "IBAN GA21 4002 1010 0000 1234 5678 90";

    private SupplierCreateRequest request(String taxId) {
        return new SupplierCreateRequest(
                "Acme Freight SA", taxId, "billing@acme.test", "+241 01 02 03 04",
                PLAINTEXT_BANK, "Owendo, Gabon", SupplierCategory.GOODS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SoD — who may write the supplier reference
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Create: ADMIN → 403 (no financial access), and the supplier is not persisted")
    @WithMockUser(roles = "ADMIN")
    void createSupplier_asAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("TAX-ADMIN-403"))))
                .andExpect(status().isForbidden());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM suppliers WHERE tax_id = ?", Integer.class, "TAX-ADMIN-403");
        org.junit.jupiter.api.Assertions.assertEquals(0, count,
                "a forbidden create must never reach the database");
    }

    @Test
    @DisplayName("Create: SUPPLIER → 403 (external role cannot manage the reference)")
    @WithMockUser(roles = "SUPPLIER")
    void createSupplier_asSupplier_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("TAX-SUP-403"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("List: ADMIN → 403 on the supplier reference")
    @WithMockUser(roles = "ADMIN")
    void listSuppliers_asAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nominal + AES-256 encryption at rest + no plaintext leak
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Create: AA → 201, bank details AES-encrypted at rest, and the response never echoes them")
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void createSupplier_asAa_encryptsBankDetailsAtRest_andNeverLeaksThem() throws Exception {
        String body = mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("TAX-AA-OK"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.companyName").value("Acme Freight SA"))
                // The response DTO deliberately omits bankDetails — it must never be echoed.
                .andExpect(jsonPath("$.data.bankDetails").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // The whole serialized response must not contain the plaintext anywhere.
        org.junit.jupiter.api.Assertions.assertFalse(body.contains(PLAINTEXT_BANK),
                "plaintext bank details must never appear in the API response");

        UUID id = UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());

        // The insert is still pending in the shared test transaction (Hibernate hasn't flushed it
        // to the DB); flush so the raw JDBC read below can see the row.
        entityManager.flush();

        // Read the RAW column, bypassing the JPA converter, to see what is actually stored.
        String stored = jdbcTemplate.queryForObject(
                "SELECT bank_details FROM suppliers WHERE id = ?", String.class, id);
        assertNotNull(stored, "bank_details must be persisted");
        assertNotEquals(PLAINTEXT_BANK, stored,
                "bank_details must be AES-encrypted at rest, never stored as plaintext");
        org.junit.jupiter.api.Assertions.assertFalse(stored.contains("IBAN"),
                "no plaintext fragment of the bank details may survive in the stored column");

        // And it must round-trip back to plaintext when read through the entity (GET by id, AA).
        mockMvc.perform(get("/api/v1/suppliers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxId").value("TAX-AA-OK"))
                .andExpect(jsonPath("$.data.bankDetails").doesNotExist());
    }
}
