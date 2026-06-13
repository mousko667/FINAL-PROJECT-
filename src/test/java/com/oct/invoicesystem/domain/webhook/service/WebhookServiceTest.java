package com.oct.invoicesystem.domain.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.webhook.dto.WebhookCreateRequest;
import com.oct.invoicesystem.domain.webhook.model.Webhook;
import com.oct.invoicesystem.domain.webhook.model.WebhookDelivery;
import com.oct.invoicesystem.domain.webhook.repository.WebhookDeliveryRepository;
import com.oct.invoicesystem.domain.webhook.repository.WebhookRepository;
import com.oct.invoicesystem.domain.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookService Tests")
class WebhookServiceTest {

    @InjectMocks
    private WebhookService webhookService;

    @Mock
    private WebhookRepository webhookRepository;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ObjectMapper objectMapper;

    private User testUser;
    private Webhook testWebhook;

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
                .secretHash("dGVzdHNlY3JldGhhc2g=")  // Base64-encoded test hash
                .events("INVOICE_SUBMITTED,INVOICE_VALIDATED")
                .isActive(true)
                .createdBy(testUser)
                .build();
    }

    @Test
    @DisplayName("Should register webhook and return raw secret once")
    void testRegisterWebhook() {
        WebhookCreateRequest request = WebhookCreateRequest.builder()
                .name("My Webhook")
                .url("https://example.com/webhook")
                .events(Arrays.asList("INVOICE_SUBMITTED", "INVOICE_VALIDATED"))
                .build();

        when(webhookRepository.save(any(Webhook.class))).thenAnswer(invocation -> {
            Webhook w = invocation.getArgument(0);
            if (w.getId() == null) w = Webhook.builder()
                    .id(UUID.randomUUID())
                    .name(w.getName())
                    .url(w.getUrl())
                    .isActive(w.getIsActive())
                    .secretHash(w.getSecretHash())
                    .events(w.getEvents())
                    .createdBy(w.getCreatedBy())
                    .build();
            return w;
        });

        var response = webhookService.registerWebhook(request, testUser);

        assertNotNull(response);
        assertEquals("My Webhook", response.getName());
        assertEquals("https://example.com/webhook", response.getUrl());
        assertNotNull(response.getSecret());  // Secret is returned once
        assertTrue(response.getIsActive());

        verify(webhookRepository).save(any(Webhook.class));
    }

    @Test
    @DisplayName("Should deactivate webhook with soft delete")
    void testDeactivateWebhook() {
        when(webhookRepository.findById(testWebhook.getId())).thenReturn(Optional.of(testWebhook));
        when(webhookRepository.save(any(Webhook.class))).thenReturn(testWebhook);

        webhookService.deactivateWebhook(testWebhook.getId());

        verify(webhookRepository).findById(testWebhook.getId());
        verify(webhookRepository).save(any(Webhook.class));
    }

    @Test
    @DisplayName("Should retrieve only active webhooks")
    void testGetActiveWebhooks() {
        List<Webhook> webhooks = Arrays.asList(testWebhook);
        when(webhookRepository.findByIsActiveTrue()).thenReturn(webhooks);

        List<Webhook> result = webhookService.getActiveWebhooks();

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsActive());
        verify(webhookRepository).findByIsActiveTrue();
    }

    @Test
    @DisplayName("Should build integration status from active webhooks and their latest delivery")
    void testGetIntegrationStatus() {
        WebhookDelivery latestDelivery = WebhookDelivery.builder()
                .id(UUID.randomUUID())
                .webhook(testWebhook)
                .eventType("INVOICE_SUBMITTED")
                .responseStatus(200)
                .success(true)
                .attemptCount(1)
                .lastAttemptedAt(Instant.now())
                .build();

        when(webhookRepository.findByIsActiveTrue()).thenReturn(List.of(testWebhook));
        when(deliveryRepository.findLatestDeliveryByWebhook(testWebhook)).thenReturn(Optional.of(latestDelivery));

        List<com.oct.invoicesystem.domain.webhook.dto.WebhookStatusResponse> result = webhookService.getIntegrationStatus();

        assertEquals(1, result.size());
        var status = result.get(0);
        assertEquals(testWebhook.getId(), status.getId());
        assertEquals(testWebhook.getName(), status.getName());
        assertEquals(List.of("INVOICE_SUBMITTED", "INVOICE_VALIDATED"), status.getEvents());
        assertEquals(200, status.getLastResponseStatus());
        assertTrue(status.getLastDeliverySuccess());
    }

    @Test
    @DisplayName("Should build integration status without delivery info when no delivery exists")
    void testGetIntegrationStatus_NoDeliveries() {
        when(webhookRepository.findByIsActiveTrue()).thenReturn(List.of(testWebhook));
        when(deliveryRepository.findLatestDeliveryByWebhook(testWebhook)).thenReturn(Optional.empty());

        List<com.oct.invoicesystem.domain.webhook.dto.WebhookStatusResponse> result = webhookService.getIntegrationStatus();

        assertEquals(1, result.size());
        assertNull(result.get(0).getLastResponseStatus());
        assertNull(result.get(0).getLastDeliverySuccess());
    }

    @Test
    @DisplayName("Should build valid HMAC-SHA256 signature")
    void testBuildSignature() {
        String payload = "{\"event\":\"INVOICE_SUBMITTED\"}";
        String secret = "dGVzdHNlY3JldA==";  // Base64-encoded "testsecret"

        String signature = webhookService.buildSignature(payload, secret);

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        // Signature should be Base64-encoded
        assertTrue(signature.matches("[A-Za-z0-9+/=]+"));
    }

    @Test
    @DisplayName("Should hash secret with SHA-256")
    void testHashSecret() {
        String secret = "testsecret";

        String hash = webhookService.hashSecret(secret);

        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        // Hash should be consistent
        assertEquals(hash, webhookService.hashSecret(secret));
    }

    @Test
    @DisplayName("Should generate unique Base64-encoded secret")
    void testGenerateSecret() {
        String secret1 = webhookService.generateSecret();
        String secret2 = webhookService.generateSecret();

        assertNotNull(secret1);
        assertNotNull(secret2);
        assertNotEquals(secret1, secret2);  // Each should be unique
        assertTrue(secret1.matches("[A-Za-z0-9+/=]+"));  // Base64
    }

    @Test
    @DisplayName("Should record successful webhook delivery")
    void testRecordDeliverySuccess() {
        String payload = "{\"test\":\"data\"}";
        int responseStatus = 200;
        int attemptCount = 1;

        webhookService.recordDeliverySuccess(testWebhook, "INVOICE_SUBMITTED", payload, responseStatus, attemptCount);

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository).save(captor.capture());

        WebhookDelivery saved = captor.getValue();
        assertEquals(testWebhook, saved.getWebhook());
        assertEquals("INVOICE_SUBMITTED", saved.getEventType());
        assertEquals(payload, saved.getPayload());
        assertEquals(responseStatus, saved.getResponseStatus());
        assertTrue(saved.getSuccess());
        assertEquals(attemptCount, saved.getAttemptCount());
    }

    @Test
    @DisplayName("Should schedule a non-blocking retry with 5s backoff instead of blocking the calling thread")
    void testDeliverWebhook_OnFailure_SchedulesRetryWithoutBlocking() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":\"123\"}");
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        long start = System.nanoTime();
        webhookService.deliverWebhook(testWebhook, "INVOICE_SUBMITTED", Map.of("id", "123"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // No Thread.sleep: the call returns immediately instead of blocking for the 5s backoff.
        assertTrue(elapsedMs < 1000, "deliverWebhook should not block for the retry backoff, took " + elapsedMs + "ms");

        // The retry is scheduled via TaskScheduler with the 5s (first retry) backoff delay.
        ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), timeCaptor.capture());
        long delayMs = timeCaptor.getValue().toEpochMilli() - Instant.now().toEpochMilli();
        assertTrue(delayMs > 4000 && delayMs <= 5000, "First retry should be scheduled ~5s out, was " + delayMs + "ms");

        // Delivery outcome is not recorded yet: it depends on the (not-yet-run) scheduled retry.
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should record success without scheduling a retry when delivery succeeds on first attempt")
    void testDeliverWebhook_OnSuccess_DoesNotScheduleRetry() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":\"123\"}");
        ResponseEntity<String> okResponse = new ResponseEntity<>("OK", HttpStatusCode.valueOf(200));
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class))).thenReturn(okResponse);

        webhookService.deliverWebhook(testWebhook, "INVOICE_SUBMITTED", Map.of("id", "123"));

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertTrue(captor.getValue().getSuccess());
    }

    @Test
    @DisplayName("Should record failed webhook delivery")
    void testRecordDeliveryFailure() {
        String payload = "{\"test\":\"data\"}";
        Integer responseStatus = 500;
        int attemptCount = 3;

        webhookService.recordDeliveryFailure(testWebhook, "INVOICE_SUBMITTED", payload, responseStatus, attemptCount, null);

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository).save(captor.capture());

        WebhookDelivery saved = captor.getValue();
        assertEquals(testWebhook, saved.getWebhook());
        assertFalse(saved.getSuccess());
        assertEquals(attemptCount, saved.getAttemptCount());
        assertEquals(responseStatus, saved.getResponseStatus());
    }
}
