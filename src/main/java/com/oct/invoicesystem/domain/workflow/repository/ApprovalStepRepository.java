package com.oct.invoicesystem.domain.workflow.repository;

import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {
    Optional<ApprovalStep> findByInvoiceIdAndStepOrder(UUID invoiceId, Integer stepOrder);
    List<ApprovalStep> findByInvoiceIdOrderByStepOrderAsc(UUID invoiceId);
    long countByInvoiceId(UUID invoiceId);
}
