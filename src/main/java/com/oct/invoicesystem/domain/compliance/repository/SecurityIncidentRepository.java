package com.oct.invoicesystem.domain.compliance.repository;

import com.oct.invoicesystem.domain.compliance.model.SecurityIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, UUID> {
    List<SecurityIncident> findByOrderByReportedAtDesc();
}
