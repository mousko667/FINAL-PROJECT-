package com.oct.invoicesystem.domain.user.controller;

import com.oct.invoicesystem.domain.auth.service.AdminSessionService;
import com.oct.invoicesystem.domain.user.dto.ActiveSessionDTO;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminSessionService adminSessionService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listActiveSessions_AsAdmin_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        ActiveSessionDTO session = new ActiveSessionDTO(
                UUID.randomUUID(), userId, "jdoe", "127.0.0.1", Instant.now(), Instant.now().plusSeconds(3600));
        when(adminSessionService.listActiveSessions()).thenReturn(List.of(session));

        mockMvc.perform(get("/api/v1/admin/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].username").value("jdoe"))
                .andExpect(jsonPath("$.data[0].ipAddress").value("127.0.0.1"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listActiveSessions_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sessions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void revokeUserSessions_AsAdmin_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/sessions/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminSessionService).revokeUserSessions(userId);
    }

    @Test
    @WithMockUser(roles = "USER")
    void revokeUserSessions_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/sessions/user/{userId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
