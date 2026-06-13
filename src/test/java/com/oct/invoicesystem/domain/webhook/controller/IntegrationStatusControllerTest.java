package com.oct.invoicesystem.domain.webhook.controller;

import com.oct.invoicesystem.domain.webhook.dto.WebhookStatusResponse;
import com.oct.invoicesystem.domain.webhook.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookService webhookService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getIntegrationStatus_AsAdmin_Returns200() throws Exception {
        WebhookStatusResponse status = WebhookStatusResponse.builder()
                .id(UUID.randomUUID())
                .name("Test Webhook")
                .url("https://webhook.example.com/receive")
                .events(List.of("INVOICE_SUBMITTED"))
                .isActive(true)
                .createdAt(Instant.now())
                .lastResponseStatus(200)
                .lastDeliverySuccess(true)
                .lastDeliveredAt(Instant.now())
                .build();
        when(webhookService.getIntegrationStatus()).thenReturn(List.of(status));

        mockMvc.perform(get("/api/v1/integrations/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Test Webhook"))
                .andExpect(jsonPath("$.data[0].lastResponseStatus").value(200))
                .andExpect(jsonPath("$.data[0].lastDeliverySuccess").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getIntegrationStatus_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/status"))
                .andExpect(status().isForbidden());
    }
}
