package com.oct.invoicesystem.domain.compliance.repository;

import com.oct.invoicesystem.domain.compliance.model.PrivacyPolicyAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivacyAcceptanceRepository extends JpaRepository<PrivacyPolicyAcceptance, UUID> {
    Optional<PrivacyPolicyAcceptance> findByUserIdAndPolicyVersion(UUID userId, String policyVersion);
}
