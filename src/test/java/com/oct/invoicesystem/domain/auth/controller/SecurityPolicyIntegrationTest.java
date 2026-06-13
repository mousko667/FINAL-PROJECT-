package com.oct.invoicesystem.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.auth.repository.ActiveSessionRepository;
import com.oct.invoicesystem.domain.auth.repository.SecurityPolicyRepository;
import com.oct.invoicesystem.domain.auth.model.ActiveSession;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for P11-40 hardening: the real DB round-trip of the PUT
 * (persistence + versioning) and the inactivity-timeout enforcement at refresh time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityPolicyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SecurityPolicyRepository securityPolicyRepository;
    @Autowired private ActiveSessionRepository activeSessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin-it")
    void updatePolicy_persistsAndVersionsToSingleActiveRow() throws Exception {
        userRepository.save(User.builder()
                .username("admin-it").email("admin-it@oct.local")
                .firstName("Admin").lastName("IT")
                .password(passwordEncoder.encode("irrelevant")).active(true).build());

        Map<String, Object> body = Map.of(
                "mfaRequired", false, "sessionTimeoutMinutes", 30,
                "maxLoginAttempts", 7, "minPasswordLength", 10);

        mockMvc.perform(put("/api/v1/admin/security-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionTimeoutMinutes").value(30))
                .andExpect(jsonPath("$.data.mfaRequired").value(false));

        // Real DB round-trip: exactly one active row, carrying the new values + an author.
        var active = securityPolicyRepository.findByIsActiveTrue().orElseThrow();
        assertThat(active.getMfaRequired()).isFalse();
        assertThat(active.getSessionTimeoutMinutes()).isEqualTo(30);
        assertThat(active.getMaxLoginAttempts()).isEqualTo(7);
        assertThat(active.getMinPasswordLength()).isEqualTo(10);
        assertThat(active.getUpdatedBy()).isNotNull();
        long activeCount = securityPolicyRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive())).count();
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void refresh_isRejectedAfterSessionExpiry() throws Exception {
        userRepository.save(User.builder()
                .username("staff-it").email("staff-it@oct.local")
                .firstName("Staff").lastName("IT")
                .password(passwordEncoder.encode("Password123!")).active(true).build());

        Map<String, Object> login = Map.of("username", "staff-it", "password", "Password123!");
        String resp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(resp).path("data").path("refreshToken").asText();
        assertThat(refreshToken).isNotBlank();

        // Simulate inactivity past the timeout: expire the active session server-side.
        ActiveSession session = activeSessionRepository.findByRefreshToken(refreshToken).orElseThrow();
        session.setExpiresAt(Instant.now().minusSeconds(60));
        activeSessionRepository.save(session);

        // Refresh must now be rejected → the frontend signs the user out.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }
}
