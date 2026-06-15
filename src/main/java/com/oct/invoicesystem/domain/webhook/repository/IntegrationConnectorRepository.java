package com.oct.invoicesystem.domain.webhook.repository;

import com.oct.invoicesystem.domain.webhook.model.IntegrationConnector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationConnectorRepository extends JpaRepository<IntegrationConnector, UUID> {
    List<IntegrationConnector> findByOrderByCreatedAtDesc();
}
