package com.oct.invoicesystem.domain.compliance.repository;

import com.oct.invoicesystem.domain.compliance.model.ComplianceChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChecklistItemRepository extends JpaRepository<ComplianceChecklistItem, UUID> {
    List<ComplianceChecklistItem> findByOrderByFrameworkAscLabelAsc();
}
