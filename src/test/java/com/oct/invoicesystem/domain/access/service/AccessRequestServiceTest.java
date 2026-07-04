package com.oct.invoicesystem.domain.access.service;

import com.oct.invoicesystem.domain.access.dto.AccessRequestCreateRequest;
import com.oct.invoicesystem.domain.access.dto.AccessRequestDTO;
import com.oct.invoicesystem.domain.access.mapper.AccessRequestMapper;
import com.oct.invoicesystem.domain.access.model.AccessRequest;
import com.oct.invoicesystem.domain.access.model.AccessRequestStatus;
import com.oct.invoicesystem.domain.access.repository.AccessRequestRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers MAJEUR-10 / PROB-098: {@code create} must reject self-service requests for
 * privileged roles (ROLE_ADMIN, ROLE_DAF), not just ROLE_SUPPLIER, before ever touching
 * the role-existence lookup (separation of duties).
 */
@ExtendWith(MockitoExtension.class)
class AccessRequestServiceTest {

    @Mock AccessRequestRepository accessRequestRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock AccessRequestMapper accessRequestMapper;
    @InjectMocks AccessRequestService accessRequestService;

    @Test
    @DisplayName("create: ROLE_ADMIN interdit en self-service (escalade de privilège)")
    void create_requestedRoleAdmin_throwsSelfServiceValidationException() {
        UUID requesterId = UUID.randomUUID();
        User requester = User.builder().id(requesterId).username("aa").build();
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));

        AccessRequestCreateRequest request = new AccessRequestCreateRequest("ROLE_ADMIN", "je veux gerer");

        assertThatThrownBy(() -> accessRequestService.create(requesterId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("self-service");

        verifyNoInteractions(roleRepository);
        verify(accessRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: ROLE_DAF interdit en self-service (approbateur financier)")
    void create_requestedRoleDaf_throwsSelfServiceValidationException() {
        UUID requesterId = UUID.randomUUID();
        User requester = User.builder().id(requesterId).username("aa").build();
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));

        AccessRequestCreateRequest request = new AccessRequestCreateRequest("ROLE_DAF", "je veux approuver");

        assertThatThrownBy(() -> accessRequestService.create(requesterId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("self-service");

        verifyNoInteractions(roleRepository);
        verify(accessRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: role benin (ROLE_VALIDATEUR_N1_DRH) passe le controle liste noire")
    void create_benignRole_proceedsPastSelfServiceCheck() {
        UUID requesterId = UUID.randomUUID();
        String requestedRole = "ROLE_VALIDATEUR_N1_DRH";
        User requester = User.builder().id(requesterId).username("aa").build();
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(roleRepository.findByName(requestedRole))
                .thenReturn(Optional.of(Role.builder().id(UUID.randomUUID()).name(requestedRole).build()));
        when(accessRequestRepository.existsByRequesterIdAndRequestedRoleAndStatus(
                requesterId, requestedRole, AccessRequestStatus.PENDING)).thenReturn(false);

        AccessRequest saved = AccessRequest.builder()
                .id(UUID.randomUUID())
                .requester(requester)
                .requestedRole(requestedRole)
                .reason("besoin metier")
                .status(AccessRequestStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        when(accessRequestRepository.save(any(AccessRequest.class))).thenReturn(saved);
        when(accessRequestMapper.toDto(saved)).thenReturn(new AccessRequestDTO(
                saved.getId(), requesterId, "aa", "  ", requestedRole, "besoin metier",
                AccessRequestStatus.PENDING, null, null, null, null, null));

        AccessRequestCreateRequest request = new AccessRequestCreateRequest(requestedRole, "besoin metier");

        AccessRequestDTO result = accessRequestService.create(requesterId, request);

        assertThat(result).isNotNull();
        assertThat(result.requestedRole()).isEqualTo(requestedRole);
        verify(accessRequestRepository).save(any(AccessRequest.class));
    }
}
