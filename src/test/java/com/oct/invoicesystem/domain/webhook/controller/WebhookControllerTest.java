package com.oct.invoicesystem.domain.webhook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.webhook.dto.WebhookCreateRequest;
import com.oct.invoicesystem.domain.webhook.dto.WebhookDeliveryResponse;
import com.oct.invoicesystem.domain.webhook.dto.WebhookResponse;
import com.oct.invoicesystem.domain.webhook.model.Webhook;
import com.oct.invoicesystem.domain.webhook.service.WebhookService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.PagedResponse;
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
                .active(true)
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
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(testUser, "password", java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))))
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

        when(webhookService.listActiveWebhooks()).thenReturn(List.of(responseWithoutSecret));

        mockMvc.perform(get("/api/v1/integrations/webhooks")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Test Webhook"))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist());

        verify(webhookService).listActiveWebhooks();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should deactivate webhook")
    void testDeactivateWebhook() throws Exception {
        UUID webhookId = testWebhook.getId();

        mockMvc.perform(delete("/api/v1/integrations/webhooks/" + webhookId))
                .andExpect(status().isOk());

        verify(webhookService).deactivateWebhook(webhookId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return 404 when webhook not found")
    void testDeactivateWebhookNotFound() throws Exception {
        UUID webhookId = UUID.randomUUID();
        doThrow(new com.oct.invoicesystem.shared.exception.ResourceNotFoundException("Webhook not found: " + webhookId))
                .when(webhookService).deactivateWebhook(webhookId);

        mockMvc.perform(delete("/api/v1/integrations/webhooks/" + webhookId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should get delivery log for webhook")
    void testGetDeliveryLog() throws Exception {
        UUID webhookId = testWebhook.getId();
        WebhookDeliveryResponse delivery = WebhookDeliveryResponse.builder()
                .id(UUID.randomUUID())
                .eventType("INVOICE_SUBMITTED")
                .responseStatus(200)
                .success(true)
                .attemptCount(1)
                .createdAt(Instant.now())
                .build();

        PagedResponse<WebhookDeliveryResponse> pagedResponse = PagedResponse.<WebhookDeliveryResponse>builder()
                .content(List.of(delivery))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();

        when(webhookService.getDeliveryLog(eq(webhookId), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/integrations/webhooks/" + webhookId + "/deliveries")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(webhookService).getDeliveryLog(eq(webhookId), any(org.springframework.data.domain.Pageable.class));
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
