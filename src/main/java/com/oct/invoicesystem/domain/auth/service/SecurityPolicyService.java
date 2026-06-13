package com.oct.invoicesystem.domain.auth.service;

import com.oct.invoicesystem.domain.auth.model.SecurityPolicy;
import com.oct.invoicesystem.domain.auth.repository.SecurityPolicyRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the system-wide {@link SecurityPolicy} (P11-40 / REQ-02). One active row at a
 * time; {@link #update} versions the policy (deactivates the old row, inserts a new one).
 *
 * <p>Read directly per call. The enforcement points (login lockout, MFA filter, password
 * validation, token TTL) call {@link #getActivePolicy()} when needed — frequent for the
 * MFA filter, so a cache is a reasonable future optimisation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SecurityPolicyService {

    private final SecurityPolicyRepository securityPolicyRepository;

    /**
     * Returns the active policy, or safe defaults if none exists. Never throws — the
     * enforcement points (login, MFA, password) must not break authentication just because
     * the policy row is missing (e.g. tests with Flyway disabled, or a fresh install).
     */
    @Transactional(readOnly = true)
    public SecurityPolicy getActivePolicy() {
        return securityPolicyRepository.findByIsActiveTrue().orElseGet(SecurityPolicyService::defaultPolicy);
    }

    private static SecurityPolicy defaultPolicy() {
        return SecurityPolicy.builder()
                .mfaRequired(true)
                .sessionTimeoutMinutes(60)
                .maxLoginAttempts(5)
                .minPasswordLength(8)
                .isActive(true)
                .build();
    }

    /**
     * Enforces the policy's minimum password length (P11-40). Call at every password-setting
     * point (create user, reset, supplier register) — replaces the static {@code @Size(min=8)}.
     *
     * @throws ValidationException if the password is shorter than the policy minimum
     */
    @Transactional(readOnly = true)
    public void validatePasswordMeetsPolicy(String rawPassword) {
        int min = getActivePolicy().getMinPasswordLength();
        if (rawPassword == null || rawPassword.length() < min) {
            throw new ValidationException("validation.password_too_short");
        }
    }

    public SecurityPolicy update(boolean mfaRequired, int sessionTimeoutMinutes,
                                 int maxLoginAttempts, int minPasswordLength, User updatedBy) {
        // Deactivate the current active row if one exists (none on a fresh install).
        securityPolicyRepository.findByIsActiveTrue().ifPresent(current -> {
            current.setIsActive(false);
            securityPolicyRepository.save(current);
        });

        SecurityPolicy updated = SecurityPolicy.builder()
                .mfaRequired(mfaRequired)
                .sessionTimeoutMinutes(sessionTimeoutMinutes)
                .maxLoginAttempts(maxLoginAttempts)
                .minPasswordLength(minPasswordLength)
                .isActive(true)
                .updatedBy(updatedBy)
                .build();
        log.info("Security policy updated by {}: mfaRequired={}, sessionTimeout={}min, maxAttempts={}, minPassword={}",
                updatedBy.getUsername(), mfaRequired, sessionTimeoutMinutes, maxLoginAttempts, minPasswordLength);
        return securityPolicyRepository.save(updated);
    }
}
