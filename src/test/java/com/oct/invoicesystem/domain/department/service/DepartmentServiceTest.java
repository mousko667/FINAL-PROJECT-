package com.oct.invoicesystem.domain.department.service;

import com.oct.invoicesystem.domain.department.dto.DepartmentCreateRequest;
import com.oct.invoicesystem.domain.department.dto.DepartmentDTO;
import com.oct.invoicesystem.domain.department.dto.DepartmentUpdateRequest;
import com.oct.invoicesystem.domain.department.mapper.DepartmentMapper;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private DepartmentService departmentService;

    private Department department;
    private DepartmentCreateRequest createReq;

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setId(UUID.randomUUID());
        department.setCode("IT");
        department.setNameFr("Informatique");
        department.setNameEn("Information Technology");
        
        createReq = new DepartmentCreateRequest("IT", "Informatique", "Information Technology", true, "ROLE_MANAGER", "ROLE_DIRECTOR");
    }

    @Test
    void createDepartment_Success() {
        when(departmentRepository.existsByCode("IT")).thenReturn(false);
        when(departmentMapper.toEntity(any())).thenReturn(department);
        when(departmentRepository.save(any(Department.class))).thenReturn(department);
        when(departmentMapper.toDto(any())).thenReturn(mock(DepartmentDTO.class));
        
        assertDoesNotThrow(() -> departmentService.createDepartment(createReq));
        
        verify(departmentRepository).save(department);
    }
    
    @Test
    void createDepartment_CodeExists_ThrowsException() {
        when(departmentRepository.existsByCode("IT")).thenReturn(true);
        
        ValidationException ex = assertThrows(ValidationException.class, () -> departmentService.createDepartment(createReq));
        assertTrue(ex.getMessage().contains("already exists"));
        
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void updateDepartment_Success() {
        UUID id = department.getId();
        DepartmentUpdateRequest updateReq = new DepartmentUpdateRequest("Tech", "Tech En", false, "R1", null);
        
        when(departmentRepository.findById(id)).thenReturn(Optional.of(department));
        when(departmentRepository.save(department)).thenReturn(department);
        when(departmentMapper.toDto(any())).thenReturn(mock(DepartmentDTO.class));
        
        assertDoesNotThrow(() -> departmentService.updateDepartment(id, updateReq));
        
        verify(departmentRepository).save(department);
        assertEquals("Tech", department.getNameFr());
        assertEquals("Tech En", department.getNameEn());
        assertFalse(department.isRequiresN2());
    }

    @Test
    void activateDepartment_Success() {
        UUID id = department.getId();
        when(departmentRepository.findById(id)).thenReturn(Optional.of(department));
        when(departmentRepository.save(department)).thenReturn(department);
        
        departmentService.activateDepartment(id, false);
        
        assertFalse(department.isActive());
        verify(departmentRepository).save(department);
    }
}
