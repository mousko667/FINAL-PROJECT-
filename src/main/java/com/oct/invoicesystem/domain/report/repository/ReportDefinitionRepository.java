package com.oct.invoicesystem.domain.report.repository;

import com.oct.invoicesystem.domain.report.model.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {
    List<ReportDefinition> findByOrderByCreatedAtDesc();
    List<ReportDefinition> findByActiveTrueAndFrequency(String frequency);
}
