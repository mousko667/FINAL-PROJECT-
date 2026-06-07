package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalDelegationRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelegationServiceTest {

    @Mock ApprovalDelegationRepository delegationRepository;
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
}
