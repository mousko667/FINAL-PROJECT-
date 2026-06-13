package com.oct.invoicesystem.domain.auth.service;

import com.oct.invoicesystem.domain.auth.model.SecurityPolicy;
import com.oct.invoicesystem.domain.auth.repository.SecurityPolicyRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityPolicyServiceTest {

    @Mock
    private SecurityPolicyRepository securityPolicyRepository;

    @InjectMocks
    private SecurityPolicyService securityPolicyService;

    private SecurityPolicy active() {
        return SecurityPolicy.builder()
                .id(UUID.randomUUID())
                .mfaRequired(true).sessionTimeoutMinutes(60)
                .maxLoginAttempts(5).minPasswordLength(8)
                .isActive(true)
                .build();
    }

    @Test
    void getActivePolicy_returnsActive() {
        SecurityPolicy p = active();
        when(securityPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.of(p));
        assertThat(securityPolicyService.getActivePolicy()).isSameAs(p);
    }

    @Test
    void getActivePolicy_noneActive_returnsSafeDefaults() {
        when(securityPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.empty());
        SecurityPolicy p = securityPolicyService.getActivePolicy();
        assertThat(p.getMfaRequired()).isTrue();
        assertThat(p.getSessionTimeoutMinutes()).isEqualTo(60);
        assertThat(p.getMaxLoginAttempts()).isEqualTo(5);
        assertThat(p.getMinPasswordLength()).isEqualTo(8);
    }

    @Test
    void update_deactivatesOldAndInsertsNewActive() {
        SecurityPolicy old = active();
        when(securityPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.of(old));
        when(securityPolicyRepository.save(any(SecurityPolicy.class))).thenAnswer(i -> i.getArgument(0));
        User admin = User.builder().id(UUID.randomUUID()).username("admin").build();

        SecurityPolicy result = securityPolicyService.update(false, 30, 7, 12, admin);

        assertThat(old.getIsActive()).isFalse();              // old deactivated
        assertThat(result.getIsActive()).isTrue();             // new is active
        assertThat(result.getMfaRequired()).isFalse();
        assertThat(result.getSessionTimeoutMinutes()).isEqualTo(30);
        assertThat(result.getMaxLoginAttempts()).isEqualTo(7);
        assertThat(result.getMinPasswordLength()).isEqualTo(12);
        ArgumentCaptor<SecurityPolicy> captor = ArgumentCaptor.forClass(SecurityPolicy.class);
        verify(securityPolicyRepository, times(2)).save(captor.capture());
    }

    @Test
    void validatePasswordMeetsPolicy_tooShort_throws() {
        when(securityPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.of(active()));
        assertThatThrownBy(() -> securityPolicyService.validatePasswordMeetsPolicy("short"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validatePasswordMeetsPolicy_longEnough_passes() {
        when(securityPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.of(active()));
        securityPolicyService.validatePasswordMeetsPolicy("longenough123");
    }
}
