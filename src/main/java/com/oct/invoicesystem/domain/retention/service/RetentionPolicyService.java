package com.oct.invoicesystem.domain.retention.service;

import com.oct.invoicesystem.domain.retention.dto.RetentionPolicyDTO;
import com.oct.invoicesystem.domain.retention.dto.RetentionPolicyRequest;
import com.oct.invoicesystem.domain.retention.model.RetentionPolicy;
import com.oct.invoicesystem.domain.retention.repository.RetentionPolicyRepository;
import com.oct.invoicesystem.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Singleton retention-policy configuration (B2, M9 #7 / M14 #6). Replaces the hard-coded
 * {@code app.retention.years} value with a DB-backed, ADMIN-editable, audited policy that the
 * {@link com.oct.invoicesystem.domain.invoice.scheduler.DocumentRetentionJob} reads at runtime.
 *
 * <p>The policy is a single row. {@link #get()} seeds it from {@code app.retention.years} on first
 * access so the system is always operable (no manual setup required).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RetentionPolicyService {

    /** Fallback used to seed the singleton when the table is empty (initial install). */
    @Value("${app.retention.years:10}")
    private int defaultRetentionYears;

    private final RetentionPolicyRepository repository;

    /** Returns the managed policy entity, seeding it on first access. For internal/job use. */
    public RetentionPolicy getEntity() {
        return repository.findFirstByOrderByCreatedAtAsc().orElseGet(this::seed);
    }

    @Transactional(readOnly = true)
    public RetentionPolicyDTO get() {
        return toDto(getEntity());
    }

    public RetentionPolicyDTO update(RetentionPolicyRequest request, User actor) {
        RetentionPolicy policy = getEntity();
        policy.setRetentionYears(request.retentionYears());
        policy.setActive(request.active());
        policy.setUpdatedBy(actor);
        return toDto(repository.save(policy));
    }

    /** Records the outcome of a retention sweep for operational visibility (called by the job). */
    public void recordSweep(Instant when, int flaggedCount) {
        RetentionPolicy policy = getEntity();
        policy.setLastSweepAt(when);
        policy.setLastFlaggedCount(flaggedCount);
        repository.save(policy);
    }

    private RetentionPolicy seed() {
        RetentionPolicy policy = RetentionPolicy.builder()
                .retentionYears(defaultRetentionYears)
                .active(true)
                .build();
        return repository.save(policy);
    }

    private RetentionPolicyDTO toDto(RetentionPolicy p) {
        return new RetentionPolicyDTO(p.getRetentionYears(), p.isActive(),
                p.getLastSweepAt(), p.getLastFlaggedCount(), p.getUpdatedAt());
    }
}
