package com.oct.invoicesystem.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.user.dto.AssignRoleRequest;
import com.oct.invoicesystem.domain.user.dto.UserCreateRequest;
import com.oct.invoicesystem.domain.user.dto.UserDTO;
import com.oct.invoicesystem.domain.user.service.UserService;
import com.oct.invoicesystem.shared.response.PagedResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    // ── GET /api/v1/users ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUsers_AsAdmin_Returns200() throws Exception {
        PagedResponse<UserDTO> response = new PagedResponse<>(Collections.emptyList(), 0, 20, 0, 0, true);
        when(userService.getUsers(0, 20, "createdAt,desc", null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void getUsers_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getUsers_AsDaf_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUsers_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/users/{id} ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_AsAdmin_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UserDTO dto = new UserDTO(id, "alice", "alice@oct.com", "Alice", "Smith", "fr", true,
                List.of("ROLE_ASSISTANT_COMPTABLE"), null, null);
        when(userService.getUserById(id)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    @WithMockUser(roles = "AUDITEUR")
    void getUserById_AsAuditeur_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserById_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/users ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_AsAdmin_Returns201() throws Exception {
        UserCreateRequest request = new UserCreateRequest(
                "newuser", "test@ex.com", "StrongP@ss1", "Alice", "Smith", "fr", List.of("ROLE_AUDITEUR"));
        UserDTO responseDto = new UserDTO(UUID.randomUUID(), "newuser", "test@ex.com",
                "Alice", "Smith", "fr", true, List.of("ROLE_AUDITEUR"), null, null);

        when(userService.createUser(any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("newuser"));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void createUser_AsNonAdmin_Returns403() throws Exception {
        UserCreateRequest request = new UserCreateRequest(
                "hacker", "h@ex.com", "pass", "H", "A", "fr", List.of("ROLE_ADMIN"));
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_Unauthenticated_Returns401() throws Exception {
        UserCreateRequest request = new UserCreateRequest(
                "hacker", "h@ex.com", "pass", "H", "A", "fr", List.of());
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/v1/users/{id} ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUser_AsAdmin_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UserDTO dto = new UserDTO(id, "updated", "u@oct.com", "Up", "Dated", "en", true,
                List.of("ROLE_AUDITEUR"), null, null);
        when(userService.updateUser(eq(id), any())).thenReturn(dto);

        String body = "{\"firstName\":\"Up\",\"lastName\":\"Dated\",\"preferredLang\":\"en\"}";
        mockMvc.perform(put("/api/v1/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("updated"));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void updateUser_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(put("/api/v1/users/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"X\",\"lastName\":\"Y\",\"preferredLang\":\"fr\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(put("/api/v1/users/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"X\",\"lastName\":\"Y\",\"preferredLang\":\"fr\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /api/v1/users/{id}/activate ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateUser_AsAdmin_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(userService).activateUser(id, true);

        mockMvc.perform(patch("/api/v1/users/{id}/activate", id)
                .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User activated successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateUser_AsAdmin_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(userService).activateUser(id, false);

        mockMvc.perform(patch("/api/v1/users/{id}/activate", id)
                .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User deactivated successfully"));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void activateUser_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/activate", UUID.randomUUID())
                .param("active", "true"))
                .andExpect(status().isForbidden());
    }

    @Test
    void activateUser_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/activate", UUID.randomUUID())
                .param("active", "true"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/v1/users/{id}/roles ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void assignRoles_AsAdmin_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        AssignRoleRequest req = new AssignRoleRequest(List.of(UUID.randomUUID()));
        doNothing().when(userService).assignRoles(eq(id), any());

        mockMvc.perform(put("/api/v1/users/{id}/roles", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Roles assigned successfully"));
    }

    @Test
    @WithMockUser(roles = "AUDITEUR")
    void assignRoles_AsNonAdmin_Returns403() throws Exception {
        AssignRoleRequest req = new AssignRoleRequest(List.of(UUID.randomUUID()));
        mockMvc.perform(put("/api/v1/users/{id}/roles", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignRoles_Unauthenticated_Returns401() throws Exception {
        AssignRoleRequest req = new AssignRoleRequest(List.of(UUID.randomUUID()));
        mockMvc.perform(put("/api/v1/users/{id}/roles", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/users/{id}/unlock ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void unlockUser_AsAdmin_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(userService).unlockUser(id);

        mockMvc.perform(post("/api/v1/users/{id}/unlock", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User account unlocked successfully"));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void unlockUser_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users/{id}/unlock", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unlockUser_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/{id}/unlock", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
