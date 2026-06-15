package com.oct.invoicesystem.domain.department.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.department.dto.DepartmentCreateRequest;
import com.oct.invoicesystem.domain.department.dto.DepartmentDTO;
import com.oct.invoicesystem.domain.department.dto.DepartmentUpdateRequest;
import com.oct.invoicesystem.domain.department.service.DepartmentService;
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
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DepartmentService departmentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "USER")
    void getDepartments_AsUser_Returns200() throws Exception {
        PagedResponse<DepartmentDTO> response = new PagedResponse<>(Collections.emptyList(), 0, 100, 0, 0, true);
        when(departmentService.getDepartments(0, 100, "code,asc")).thenReturn(response);

        mockMvc.perform(get("/api/v1/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createDepartment_AsAdmin_Returns201() throws Exception {
        DepartmentCreateRequest request = new DepartmentCreateRequest("IT", "Info", "IT", false, "ROLE_M", null);
        DepartmentDTO responseDto = new DepartmentDTO(UUID.randomUUID(), "IT", "Info", "IT", false, "ROLE_M", null, true, null, null, null);

        when(departmentService.createDepartment(any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("IT"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createDepartment_AsUser_Returns403() throws Exception {
        DepartmentCreateRequest request = new DepartmentCreateRequest("IT", "Info", "IT", false, "ROLE_M", null);
        
        mockMvc.perform(post("/api/v1/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDepartment_AsAdmin_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        DepartmentUpdateRequest request = new DepartmentUpdateRequest("New Name", null, null, null, null, null);
        DepartmentDTO responseDto = new DepartmentDTO(id, "IT", "New Name", "IT", false, "ROLE_M", null, true, null, null, null);
        
        when(departmentService.updateDepartment(eq(id), any())).thenReturn(responseDto);

        mockMvc.perform(put("/api/v1/departments/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nameFr").value("New Name"));
    }
}
