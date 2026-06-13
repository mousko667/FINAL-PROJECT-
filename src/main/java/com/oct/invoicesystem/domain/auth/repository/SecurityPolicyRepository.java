package com.oct.invoicesystem.domain.auth.repository;

import com.oct.invoicesystem.domain.auth.model.SecurityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicy, UUID> {
    Optional<SecurityPolicy> findByIsActiveTrue();
}
