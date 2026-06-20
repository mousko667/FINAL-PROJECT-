package com.oct.invoicesystem.domain.retention.repository;

import com.oct.invoicesystem.domain.retention.model.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

    /** The policy is a singleton; the oldest (and only) row is the active one. */
    Optional<RetentionPolicy> findFirstByOrderByCreatedAtAsc();
}
