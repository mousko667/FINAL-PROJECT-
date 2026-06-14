package com.oct.invoicesystem.domain.user.service;

import com.oct.invoicesystem.domain.user.dto.AssignRoleRequest;
import com.oct.invoicesystem.domain.user.dto.UserCreateRequest;
import com.oct.invoicesystem.domain.user.dto.UserDTO;
import com.oct.invoicesystem.domain.user.dto.UserUpdateRequest;
import com.oct.invoicesystem.domain.user.mapper.UserMapper;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.auth.service.SecurityPolicyService;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityPolicyService securityPolicyService;

    @Transactional(readOnly = true)
    public PagedResponse<UserDTO> getUsers(int page, int size, String sort) {
        String[] sortParams = sort.split(",");
        String sortBy = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc") 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC;
                
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<User> usersPage = userRepository.findAll(pageable);
        
        List<UserDTO> userDTOs = usersPage.getContent().stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
                
        return new PagedResponse<>(
                userDTOs,
                usersPage.getNumber(),
                usersPage.getSize(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDTO createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ValidationException("Username already exists: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ValidationException("Email already exists: " + request.email());
        }

        securityPolicyService.validatePasswordMeetsPolicy(request.password());
        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user.setUserRoles(new HashSet<>());

        // Save first so the user gets a UUID, then attach roles
        User savedUser = userRepository.save(user);

        if (request.roles() != null && !request.roles().isEmpty()) {
            for (String roleName : request.roles()) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
                UserRole userRole = new UserRole();
                userRole.setId(new com.oct.invoicesystem.domain.user.model.UserRoleId(savedUser.getId(), role.getId()));
                userRole.setUser(savedUser);
                userRole.setRole(role);
                savedUser.getUserRoles().add(userRole);
            }
            savedUser = userRepository.save(savedUser);
        }

        return userMapper.toDto(savedUser);
    }

    @Transactional
    public UserDTO updateUser(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new ValidationException("Email already exists: " + request.email());
            }
            user.setEmail(request.email());
        }

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.preferredLang() != null) user.setPreferredLang(request.preferredLang());
        if (request.employeeId() != null) user.setEmployeeId(request.employeeId());
        if (request.departmentId() != null) user.setDepartmentId(request.departmentId());
        if (request.approvalLimit() != null) user.setApprovalLimit(request.approvalLimit());

        if (request.roles() != null) {
            user.getUserRoles().clear();
            for (String roleName : request.roles()) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
                UserRole userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(role);
                user.getUserRoles().add(userRole);
            }
        }

        User updatedUser = userRepository.save(user);
        return userMapper.toDto(updatedUser);
    }

    @Transactional
    public void assignRoles(UUID id, AssignRoleRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.getUserRoles().clear();
        if (request.roleIds() != null) {
            for (UUID roleId : request.roleIds()) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));
                UserRole userRole = new UserRole();
                // The @EmbeddedId composite key must be set explicitly, otherwise Hibernate cannot
                // populate UserRoleId.roleId on flush (NPE / JpaSystemException). Same pattern as createUser.
                userRole.setId(new com.oct.invoicesystem.domain.user.model.UserRoleId(user.getId(), role.getId()));
                userRole.setUser(user);
                userRole.setRole(role);
                user.getUserRoles().add(userRole);
            }
        }

        userRepository.save(user);
    }

    @Transactional
    public void activateUser(UUID id, boolean isActive) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setActive(isActive);
        if (!isActive) {
            user.setDeletedAt(Instant.now());
        } else {
            user.setDeletedAt(null);
        }
        
        userRepository.save(user);
    }

    @Transactional
    public User updateProfile(UUID userId, String firstName, String lastName, String preferredLang) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (preferredLang != null) user.setPreferredLang(preferredLang);
        User saved = userRepository.save(user);
        auditService.logAction(userId, "USER", userId.toString(), "PROFILE_UPDATE", null, null, null, null);
        return saved;
    }

    @Transactional
    public void unlockUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        
        userRepository.save(user);
    }
}
