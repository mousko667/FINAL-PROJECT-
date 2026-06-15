package com.oct.invoicesystem.domain.department.service;

import com.oct.invoicesystem.domain.department.dto.DepartmentCreateRequest;
import com.oct.invoicesystem.domain.department.dto.DepartmentDTO;
import com.oct.invoicesystem.domain.department.dto.DepartmentUpdateRequest;
import com.oct.invoicesystem.domain.department.mapper.DepartmentMapper;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    @Transactional(readOnly = true)
    public PagedResponse<DepartmentDTO> getDepartments(int page, int size, String sort) {
        String[] sortParams = sort.split(",");
        String sortBy = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<Department> deptsPage = departmentRepository.findAll(pageable);

        List<DepartmentDTO> dtos = deptsPage.getContent().stream()
                .map(departmentMapper::toDto)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                dtos,
                deptsPage.getNumber(),
                deptsPage.getSize(),
                deptsPage.getTotalElements(),
                deptsPage.getTotalPages(),
                deptsPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public DepartmentDTO getDepartmentById(UUID id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
        return departmentMapper.toDto(dept);
    }

    @Transactional
    public DepartmentDTO createDepartment(DepartmentCreateRequest request) {
        if (departmentRepository.existsByCode(request.code())) {
            throw new ValidationException("Department code already exists: " + request.code());
        }

        Department dept = departmentMapper.toEntity(request);
        dept.setActive(true);

        return departmentMapper.toDto(departmentRepository.save(dept));
    }

    @Transactional
    public DepartmentDTO updateDepartment(UUID id, DepartmentUpdateRequest request) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));

        if (request.nameFr() != null) dept.setNameFr(request.nameFr());
        if (request.nameEn() != null) dept.setNameEn(request.nameEn());
        if (request.requiresN2() != null) dept.setRequiresN2(request.requiresN2());
        if (request.n1Role() != null) dept.setN1Role(request.n1Role());
        if (request.n2Role() != null) dept.setN2Role(request.n2Role());
        if (request.budget() != null) dept.setBudget(request.budget());

        return departmentMapper.toDto(departmentRepository.save(dept));
    }

    @Transactional
    public void activateDepartment(UUID id, boolean isActive) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
        dept.setActive(isActive);
        departmentRepository.save(dept);
    }
}
