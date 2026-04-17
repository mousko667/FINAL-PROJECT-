package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchingConfigRepository extends JpaRepository<MatchingConfig, UUID> {

    Optional<MatchingConfig> findByIsActiveTrue();
}
