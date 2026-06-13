package com.oct.invoicesystem.domain.workflow.repository;

import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {
    Optional<ApprovalStep> findByInvoiceIdAndStepOrder(UUID invoiceId, Integer stepOrder);
    List<ApprovalStep> findByInvoiceIdOrderByStepOrderAsc(UUID invoiceId);
    long countByInvoiceId(UUID invoiceId);

    @Query("SELECT s FROM ApprovalStep s WHERE s.status = :status AND s.deadline IS NOT NULL AND s.deadline < :before")
    List<ApprovalStep> findByStatusAndDeadlineBefore(ApprovalStepStatus status, Instant before);

    // Validator-scoped dashboard stats (REQ-04): steps acted on by a given approver.
    long countByApproverIdAndStatus(UUID approverId, ApprovalStepStatus status);

    long countByApproverIdAndStatusInAndActionAtGreaterThanEqual(
            UUID approverId, Collection<ApprovalStepStatus> statuses, Instant from);
}
