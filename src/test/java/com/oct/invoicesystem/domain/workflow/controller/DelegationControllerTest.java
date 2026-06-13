package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.service.DelegationService;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("DelegationController Tests")
class DelegationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DelegationService delegationService;

    @MockBean
    private UserRepository userRepository;

    private User admin;
    private User delegator;
    private User delegatee;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(UUID.randomUUID()).username("admin").password("x").active(true).build();
        delegator = User.builder().id(UUID.randomUUID()).username("a").password("x").active(true).build();
        delegatee = User.builder().id(UUID.randomUUID()).username("b").password("x").active(true).build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
    }

    private Map<String, Object> createRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("delegatorId", delegator.getId().toString());
        request.put("delegateeId", delegatee.getId().toString());
        request.put("departmentCode", "INFO");
        request.put("fromDate", LocalDate.now().toString());
        request.put("toDate", LocalDate.now().plusDays(7).toString());
        request.put("reason", "congés");
        return request;
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    @DisplayName("Should create delegation as ADMIN and return 201 with DTO")
    void createDelegation_AsAdmin_Returns201() throws Exception {
        ApprovalDelegation saved = ApprovalDelegation.builder()
                .id(UUID.randomUUID())
                .delegator(delegator)
                .delegatee(delegatee)
                .departmentCode("INFO")
                .fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(7))
                .reason("congés")
                .createdAt(Instant.now())
                .build();

        when(delegationService.createDelegation(
                eq(delegator.getId()), eq(delegatee.getId()), eq("INFO"),
                any(LocalDate.class), any(LocalDate.class), eq("congés"), eq(admin)))
                .thenReturn(saved);

        mockMvc.perform(post("/api/v1/approvals/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.data.delegatorUsername").value("a"))
                .andExpect(jsonPath("$.data.delegateeUsername").value("b"))
                .andExpect(jsonPath("$.data.departmentCode").value("INFO"))
                .andExpect(jsonPath("$.data.reason").value("congés"));
    }

    @Test
    @WithMockUser(roles = "USER", username = "user")
    @DisplayName("Should return 403 when creating delegation as non-ADMIN")
    void createDelegation_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/approvals/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    @DisplayName("Should return 404 when delegator or delegatee is not found")
    void createDelegation_UnknownUser_Returns404() throws Exception {
        when(delegationService.createDelegation(
                eq(delegator.getId()), eq(delegatee.getId()), eq("INFO"),
                any(LocalDate.class), any(LocalDate.class), eq("congés"), eq(admin)))
                .thenThrow(new ResourceNotFoundException("Delegator not found"));

        mockMvc.perform(post("/api/v1/approvals/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    @DisplayName("Should list active delegations as ADMIN")
    void listDelegations_AsAdmin_Returns200() throws Exception {
        ApprovalDelegation delegation = ApprovalDelegation.builder()
                .id(UUID.randomUUID())
                .delegator(delegator)
                .delegatee(delegatee)
                .departmentCode("INFO")
                .fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(7))
                .reason("congés")
                .createdAt(Instant.now())
                .build();

        when(delegationService.getActiveDelegationsForDepartment("INFO")).thenReturn(List.of(delegation));

        mockMvc.perform(get("/api/v1/approvals/delegations").param("departmentCode", "INFO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].delegatorUsername").value("a"))
                .andExpect(jsonPath("$.data[0].delegateeUsername").value("b"));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    @DisplayName("Should revoke delegation as ADMIN")
    void revokeDelegation_AsAdmin_Returns200() throws Exception {
        UUID delegationId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/approvals/delegations/" + delegationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(delegationService).revokeDelegation(delegationId);
    }

    @Test
    @WithMockUser(roles = "USER", username = "user")
    @DisplayName("Should return 403 when revoking delegation as non-ADMIN")
    void revokeDelegation_AsNonAdmin_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/approvals/delegations/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
