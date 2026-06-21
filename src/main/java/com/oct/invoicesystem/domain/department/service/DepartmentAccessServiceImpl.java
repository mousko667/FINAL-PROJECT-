package com.oct.invoicesystem.domain.department.service;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;
import com.oct.invoicesystem.domain.department.dto.DepartmentUserDTO;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentAccessServiceImpl implements DepartmentAccessService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentAccessDTO> getDepartmentAccessOverview() {
        List<Department> departments = departmentRepository.findAll().stream()
                .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
                .toList();

        List<UUID> ids = departments.stream().map(Department::getId).toList();
        Map<UUID, List<User>> byDept = ids.isEmpty()
                ? Map.of()
                : userRepository.findByDepartmentIdIn(ids).stream()
                        .collect(Collectors.groupingBy(User::getDepartmentId));

        return departments.stream()
                .map(d -> toDto(d, byDept.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    private DepartmentAccessDTO toDto(Department d, List<User> users) {
        List<DepartmentUserDTO> userDtos = users.stream()
                .map(this::toUserDto)
                .sorted((a, b) -> a.username().compareToIgnoreCase(b.username()))
                .toList();
        int activeCount = (int) users.stream().filter(User::isActive).count();
        return new DepartmentAccessDTO(
                d.getId(), d.getCode(), d.getNameFr(), d.getNameEn(),
                d.isRequiresN2(), d.getN1Role(), d.getN2Role(),
                users.size(), activeCount, userDtos);
    }

    private DepartmentUserDTO toUserDto(User u) {
        List<String> roles = u.getUserRoles() == null ? List.of()
                : u.getUserRoles().stream().map(ur -> ur.getRole().getName()).sorted().toList();
        return new DepartmentUserDTO(
                u.getId(), u.getFirstName() + " " + u.getLastName(), u.getUsername(), u.isActive(), roles);
    }
}
