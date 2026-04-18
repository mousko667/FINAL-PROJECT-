package com.oct.invoicesystem.domain.webhook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.webhook.dto.WebhookCreateRequest;
import com.oct.invoicesystem.domain.webhook.dto.WebhookResponse;
import com.oct.invoicesystem.domain.webhook.mapper.WebhookMapper;
import com.oct.invoicesystem.domain.webhook.model.Webhook;
import com.oct.invoicesystem.domain.webhook.model.WebhookDelivery;
import com.oct.invoicesystem.domain.webhook.repository.WebhookDeliveryRepository;
import com.oct.invoicesystem.domain.webhook.repository.WebhookRepository;
import com.oct.invoicesystem.domain.webhook.service.WebhookService;
import com.oct.invoicesystem.domain.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WebhookController Tests")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookService webhookService;

    @MockBean
    private WebhookRepository webhookRepository;

    @MockBean
    private WebhookDeliveryRepository deliveryRepository;

    @MockBean
    private WebhookMapper webhookMapper;

    private User testUser;
    private Webhook testWebhook;
    private WebhookResponse testResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .email("admin@test.com")
                .firstName("Admin")
                .lastName("User")
                .isActive(true)
                .build();

        testWebhook = Webhook.builder()
                .id(UUID.randomUUID())
                .name("Test Webhook")
                .url("https://webhook.example.com/receive")
                .secretHash("dGVzdHNlY3JldGhhc2g=")
                .events("INVOICE_SUBMITTED,INVOICE_VALIDATED")
                .isActive(true)
                .createdBy(testUser)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testResponse = WebhookResponse.builder()
                .id(testWebhook.getId())
                .name("Test Webhook")
                .url("https://webhook.example.com/receive")
                .secret("raw-secret-shown-once")
                .events(Arrays.asList("INVOICE_SUBMITTED", "INVOICE_VALIDATED"))
                .isActive(true)
                .createdAt(testWebhook.getCreatedAt())
                .updatedAt(testWebhook.getUpdatedAt())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should register webhook and return 201 with secret")
    void testRegisterWebhook() throws Exception {
        WebhookCreateRequest request = WebhookCreateRequest.builder()
                .name("My Webhook")
                .url("https://example.com/webhook")
                .events(Arrays.asList("INVOICE_SUBMITTED"))
                .build();

        when(webhookService.registerWebhook(any(WebhookCreateRequest.class), any(User.class)))
                .thenReturn(testResponse);

        mockMvc.perform(post("/api/v1/integrations/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("Test Webhook"))
                .andExpect(jsonPath("$.data.secret").value("raw-secret-shown-once"));

        verify(webhookService).registerWebhook(any(WebhookCreateRequest.class), any(User.class));
    }

    @Test
    @DisplayName("Should return 401 if not authenticated")
    void testRegisterWebhookUnauthorized() throws Exception {
        WebhookCreateRequest request = WebhookCreateRequest.builder()
                .name("My Webhook")
                .url("https://example.com/webhook")
                .events(Arrays.asList("INVOICE_SUBMITTED"))
                .build();

        mockMvc.perform(post("/api/v1/integrations/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should list active webhooks without secret")
    void testListWebhooks() throws Exception {
        WebhookResponse responseWithoutSecret = WebhookResponse.builder()
                .id(testWebhook.getId())
                .name("Test Webhook")
                .url("https://webhook.example.com/receive")
                .secret(null)  // Secret NOT included
                .events(Arrays.asList("INVOICE_SUBMITTED", "INVOICE_VALIDATED"))
                .isActive(true)
                .createdAt(testWebhook.getCreatedAt())
                .updatedAt(testWebhook.getUpdatedAt())
                .build();

        when(webhookRepository.findByIsActiveTrue()).thenReturn(Arrays.asList(testWebhook));
        when(webhookMapper.toResponseWithoutSecret(any(Webhook.class))).thenReturn(responseWithoutSecret);

        mockMvc.perform(get("/api/v1/integrations/webhooks")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Test Webhook"))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist());

        verify(webhookRepository).findByIsActiveTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should deactivate webhook")
    void testDeactivateWebhook() throws Exception {
        UUID webhookId = testWebhook.getId();
        when(webhookRepository.findById(webhookId)).thenReturn(Optional.of(testWebhook));

        mockMvc.perform(delete("/api/v1/integrations/webhooks/" + webhookId))
                .andExpect(status().isOk());

        verify(webhookRepository).findById(webhookId);
        verify(webhookService).deactivateWebhook(webhookId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return 404 when webhook not found")
    void testDeactivateWebhookNotFound() throws Exception {
        UUID webhookId = UUID.randomUUID();
        when(webhookRepository.findById(webhookId)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/integrations/webhooks/" + webhookId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should get delivery log for webhook")
    void testGetDeliveryLog() throws Exception {
        UUID webhookId = testWebhook.getId();
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id(UUID.randomUUID())
                .webhook(testWebhook)
                .eventType("INVOICE_SUBMITTED")
                .payload("{\"invoiceId\":\"123\"}")
                .responseStatus(200)
                .success(true)
                .attemptCount(1)
                .createdAt(Instant.now())
                .build();

        when(webhookRepository.findById(webhookId)).thenReturn(Optional.of(testWebhook));

        mockMvc.perform(get("/api/v1/integrations/webhooks/" + webhookId + "/deliveries")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(webhookRepository).findById(webhookId);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Should return 403 if user is not ADMIN")
    void testForbiddenForNonAdmin() throws Exception {
        WebhookCreateRequest request = WebhookCreateRequest.builder()
                .name("My Webhook")
                .url("https://example.com/webhook")
                .events(Arrays.asList("INVOICE_SUBMITTED"))
                .build();

        mockMvc.perform(post("/api/v1/integrations/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
