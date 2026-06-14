package com.oct.invoicesystem.domain.access.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.access.dto.AccessRequestCreateRequest;
import com.oct.invoicesystem.domain.access.dto.AccessRequestDTO;
import com.oct.invoicesystem.domain.access.model.AccessRequestStatus;
import com.oct.invoicesystem.domain.access.service.AccessRequestService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.PagedResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessRequestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AccessRequestService accessRequestService;
    @MockBean private SecurityHelper securityHelper;

    private AccessRequestDTO sampleDto() {
        return new AccessRequestDTO(UUID.randomUUID(), UUID.randomUUID(), "user", "Staff User",
                "ROLE_DAF", "reason", AccessRequestStatus.PENDING, null, null, null, null, null);
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void create_AsStaff_Returns201() throws Exception {
        when(securityHelper.currentUserId(any())).thenReturn(UUID.randomUUID());
        when(accessRequestService.create(any(), any())).thenReturn(sampleDto());

        mockMvc.perform(post("/api/v1/access-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AccessRequestCreateRequest("ROLE_DAF", "reason"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestedRole").value("ROLE_DAF"));
    }

    @Test
    @WithMockUser(roles = "SUPPLIER")
    void create_AsSupplier_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/access-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AccessRequestCreateRequest("ROLE_DAF", "reason"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void listQueue_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/access-requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listQueue_AsAdmin_Returns200() throws Exception {
        PagedResponse<AccessRequestDTO> page =
                new PagedResponse<>(Collections.emptyList(), 0, 20, 0, 0, true);
        when(accessRequestService.list(eq(AccessRequestStatus.PENDING), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/access-requests").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void review_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/access-requests/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void listMine_AsStaff_Returns200() throws Exception {
        when(securityHelper.currentUserId(any())).thenReturn(UUID.randomUUID());
        PagedResponse<AccessRequestDTO> page =
                new PagedResponse<>(Collections.emptyList(), 0, 20, 0, 0, true);
        when(accessRequestService.listMine(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/access-requests/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
