package com.oct.invoicesystem.domain.access.service;

import com.oct.invoicesystem.domain.access.dto.AccessRequestCreateRequest;
import com.oct.invoicesystem.domain.access.dto.AccessRequestDTO;
import com.oct.invoicesystem.domain.access.dto.AccessRequestReviewRequest;
import com.oct.invoicesystem.domain.access.mapper.AccessRequestMapper;
import com.oct.invoicesystem.domain.access.model.AccessRequest;
import com.oct.invoicesystem.domain.access.model.AccessRequestStatus;
import com.oct.invoicesystem.domain.access.repository.AccessRequestRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Self-service access-request workflow (P11-17 / REQ-23 item 3). A staff user requests one
 * additional role; an ADMIN approves (the role is added to the requester) or rejects it.
 */
@Service
@RequiredArgsConstructor
public class AccessRequestService {

    // ROLE_SUPPLIER is portal-managed and never assignable via this workflow.
    private static final String ROLE_SUPPLIER = "ROLE_SUPPLIER";

    private final AccessRequestRepository accessRequestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccessRequestMapper accessRequestMapper;

    @Transactional
    public AccessRequestDTO create(UUID requesterId, AccessRequestCreateRequest request) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterId));

        String requestedRole = request.requestedRole();
        if (ROLE_SUPPLIER.equals(requestedRole)) {
            throw new ValidationException("Role cannot be requested via self-service: " + requestedRole);
        }
        // The role must exist in the catalogue (authoritative list of assignable roles).
        if (roleRepository.findByName(requestedRole).isEmpty()) {
            throw new ValidationException("Unknown role: " + requestedRole);
        }
        // Don't request a role you already hold.
        boolean alreadyHasRole = requester.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getName().equals(requestedRole));
        if (alreadyHasRole) {
            throw new ValidationException("You already have this role: " + requestedRole);
        }
        // One open request per role at a time.
        if (accessRequestRepository.existsByRequesterIdAndRequestedRoleAndStatus(
                requesterId, requestedRole, AccessRequestStatus.PENDING)) {
            throw new ValidationException("A pending request for this role already exists");
        }

        AccessRequest entity = AccessRequest.builder()
                .requester(requester)
                .requestedRole(requestedRole)
                .reason(request.reason())
                .status(AccessRequestStatus.PENDING)
                .build();

        return accessRequestMapper.toDto(accessRequestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PagedResponse<AccessRequestDTO> list(AccessRequestStatus status, Pageable pageable) {
        Page<AccessRequest> page = status != null
                ? accessRequestRepository.findByStatus(status, pageable)
                : accessRequestRepository.findAll(pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AccessRequestDTO> listMine(UUID requesterId, Pageable pageable) {
        return toPagedResponse(accessRequestRepository.findByRequesterId(requesterId, pageable));
    }

    @Transactional
    public AccessRequestDTO review(UUID requestId, UUID reviewerId, AccessRequestReviewRequest request) {
        AccessRequest accessRequest = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found: " + requestId));

        if (accessRequest.getStatus() != AccessRequestStatus.PENDING) {
            throw new ValidationException("Access request has already been reviewed");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + reviewerId));

        boolean approve = Boolean.TRUE.equals(request.approve());
        if (approve) {
            grantRole(accessRequest.getRequester(), accessRequest.getRequestedRole());
            accessRequest.setStatus(AccessRequestStatus.APPROVED);
        } else {
            accessRequest.setStatus(AccessRequestStatus.REJECTED);
        }
        accessRequest.setReviewedBy(reviewer);
        accessRequest.setReviewComment(request.comment());
        accessRequest.setReviewedAt(Instant.now());

        return accessRequestMapper.toDto(accessRequestRepository.save(accessRequest));
    }

    /**
     * Adds {@code roleName} to {@code user} if not already present. Mirrors the role-attach pattern
     * in UserService (explicit {@link UserRoleId} composite key — see PROB-040).
     */
    private void grantRole(User user, String roleName) {
        boolean alreadyHasRole = user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getName().equals(roleName));
        if (alreadyHasRole) {
            return;
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), role.getId()));
        userRole.setUser(user);
        userRole.setRole(role);
        user.getUserRoles().add(userRole);
        userRepository.save(user);
    }

    private PagedResponse<AccessRequestDTO> toPagedResponse(Page<AccessRequest> page) {
        return new PagedResponse<>(
                page.getContent().stream().map(accessRequestMapper::toDto).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
