package com.oct.invoicesystem.domain.supplier.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.supplier.dto.SupplierCreateRequest;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupplierIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateSuspendAndGetMetricsForSupplier() throws Exception {
        // 1. Create Supplier
        SupplierCreateRequest createReq = new SupplierCreateRequest(
                "Acme Integration",
                "TAX-INT-999",
                "acme.int@example.com",
                "+123456789",
                "BANK123",
                "123 Integration St"
        );

        String createResponse = mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyName").value("Acme Integration"))
                .andExpect(jsonPath("$.data.status").value(SupplierStatus.PENDING_VERIFICATION.name()))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).get("data").get("id").asText();

        // 2. Suspend Supplier
        mockMvc.perform(post("/api/v1/suppliers/{id}/suspend", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Verify Suspension in DB via Get
        mockMvc.perform(get("/api/v1/suppliers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(SupplierStatus.SUSPENDED.name()));

        // 4. Retrieve Metrics
        mockMvc.perform(get("/api/v1/suppliers/{id}/performance", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accuracyRate").exists());
                
        // Cleanup
        supplierRepository.deleteById(java.util.UUID.fromString(id));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldPersistAndFilterByCategory() throws Exception {
        // B5: a supplier carries a spend-type category (GOODS/SERVICES/WORKS/CONSULTING) that
        // round-trips through create and acts as a directory filter.
        SupplierCreateRequest createReq = new SupplierCreateRequest(
                "Cat Services Co", "TAX-CAT-001", "cat.services@example.com",
                "+100000000", "BANKCAT", "1 Cat St",
                com.oct.invoicesystem.domain.supplier.model.SupplierCategory.SERVICES);

        String createResponse = mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("SERVICES"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createResponse).get("data").get("id").asText();

        // Filter by the matching category → supplier is present
        mockMvc.perform(get("/api/v1/suppliers").param("category", "SERVICES").param("taxId", "TAX-CAT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].category").value("SERVICES"));

        // Filter by a different category → supplier is filtered out
        mockMvc.perform(get("/api/v1/suppliers").param("category", "GOODS").param("taxId", "TAX-CAT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        supplierRepository.deleteById(java.util.UUID.fromString(id));
    }
}
