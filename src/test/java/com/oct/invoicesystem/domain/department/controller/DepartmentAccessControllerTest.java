package com.oct.invoicesystem.domain.department.controller;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;
import com.oct.invoicesystem.domain.department.service.DepartmentAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepartmentAccessControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean DepartmentAccessService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void overview_AsAdmin_Returns200() throws Exception {
        when(service.getDepartmentAccessOverview()).thenReturn(List.of(
                new DepartmentAccessDTO(UUID.randomUUID(), "IT", "Info", "IT", true, "ROLE_N1", "ROLE_N2", 0, 0, List.of())));

        mockMvc.perform(get("/api/v1/admin/department-access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("IT"));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void overview_AsDaf_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/department-access"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void overview_Anonymous_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/department-access"))
                .andExpect(status().isUnauthorized());
    }
}
