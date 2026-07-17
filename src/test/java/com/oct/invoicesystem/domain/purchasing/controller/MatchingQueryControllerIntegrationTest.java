package com.oct.invoicesystem.domain.purchasing.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchingQueryControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void list_asStaff_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPPLIER")
    void list_asSupplier_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_asAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_VALIDATEUR_N1_DRH")
    void list_asValidator_returns403() throws Exception {
        // N25: the 3-way matching (a financial activity) is reserved to AA + DAF; department
        // validators must no longer reach it.
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void list_asDaf_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isOk());
    }

    @Test
    void list_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void lines_unknownInvoice_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/matching/00000000-0000-0000-0000-000000000000/lines"))
                .andExpect(status().isNotFound());
    }
}
