package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalDelegationRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelegationServiceTest {

    @Mock ApprovalDelegationRepository delegationRepository;
    @Mock UserRepository userRepository;
    @InjectMocks DelegationService delegationService;

    @Test
    @DisplayName("createDelegation: auto-délégation interdit")
    void createDelegation_selfDelegation_throwsValidationException() {
        User user = User.builder().id(UUID.randomUUID()).username("user").build();
        assertThatThrownBy(() ->
                delegationService.createDelegation(user, user, "INFO",
                        LocalDate.now(), LocalDate.now().plusDays(7), "congés", user))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("createDelegation: date fin avant date début interdit")
    void createDelegation_invalidDates_throwsValidationException() {
        User delegator = User.builder().id(UUID.randomUUID()).username("a").build();
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").build();
        assertThatThrownBy(() ->
                delegationService.createDelegation(delegator, delegatee, "INFO",
                        LocalDate.now().plusDays(7), LocalDate.now(), "test", delegator))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("createDelegation: délégation valide persistée")
    void createDelegation_valid_persistsDelegation() {
        User delegator = User.builder().id(UUID.randomUUID()).username("a").build();
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").build();
        ApprovalDelegation saved = ApprovalDelegation.builder()
                .id(UUID.randomUUID()).delegator(delegator).delegatee(delegatee)
                .departmentCode("INFO").fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(7)).build();
        when(delegationRepository.save(any())).thenReturn(saved);

        ApprovalDelegation result = delegationService.createDelegation(
                delegator, delegatee, "INFO",
                LocalDate.now(), LocalDate.now().plusDays(7), "congés annuels", delegator);

        assertThat(result.getId()).isNotNull();
        verify(delegationRepository).save(any(ApprovalDelegation.class));
    }

    @Test
    @DisplayName("createDelegation(byIds): résout les utilisateurs et persiste (P1-05)")
    void createDelegationByIds_valid_resolvesUsersAndPersists() {
        UUID delegatorId = UUID.randomUUID();
        UUID delegateeId = UUID.randomUUID();
        User delegator = User.builder().id(delegatorId).username("a").build();
        User delegatee = User.builder().id(delegateeId).username("b").build();
        User admin = User.builder().id(UUID.randomUUID()).username("admin").build();
        when(userRepository.findById(delegatorId)).thenReturn(Optional.of(delegator));
        when(userRepository.findById(delegateeId)).thenReturn(Optional.of(delegatee));
        ApprovalDelegation saved = ApprovalDelegation.builder()
                .id(UUID.randomUUID()).delegator(delegator).delegatee(delegatee)
                .departmentCode("INFO").fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(7)).build();
        when(delegationRepository.save(any())).thenReturn(saved);

        ApprovalDelegation result = delegationService.createDelegation(
                delegatorId, delegateeId, "INFO",
                LocalDate.now(), LocalDate.now().plusDays(7), "congés annuels", admin);

        assertThat(result.getId()).isNotNull();
        verify(userRepository).findById(delegatorId);
        verify(userRepository).findById(delegateeId);
        verify(delegationRepository).save(any(ApprovalDelegation.class));
    }

    @Test
    @DisplayName("createDelegation(byIds): délégant introuvable -> ResourceNotFoundException")
    void createDelegationByIds_delegatorNotFound_throwsResourceNotFound() {
        UUID delegatorId = UUID.randomUUID();
        UUID delegateeId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).username("admin").build();
        when(userRepository.findById(delegatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                delegationService.createDelegation(delegatorId, delegateeId, "INFO",
                        LocalDate.now(), LocalDate.now().plusDays(7), "congés", admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Delegator");
        verify(delegationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDelegation(byIds): délégataire introuvable -> ResourceNotFoundException")
    void createDelegationByIds_delegateeNotFound_throwsResourceNotFound() {
        UUID delegatorId = UUID.randomUUID();
        UUID delegateeId = UUID.randomUUID();
        User delegator = User.builder().id(delegatorId).username("a").build();
        User admin = User.builder().id(UUID.randomUUID()).username("admin").build();
        when(userRepository.findById(delegatorId)).thenReturn(Optional.of(delegator));
        when(userRepository.findById(delegateeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                delegationService.createDelegation(delegatorId, delegateeId, "INFO",
                        LocalDate.now(), LocalDate.now().plusDays(7), "congés", admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Delegatee");
        verify(delegationRepository, never()).save(any());
    }
}
