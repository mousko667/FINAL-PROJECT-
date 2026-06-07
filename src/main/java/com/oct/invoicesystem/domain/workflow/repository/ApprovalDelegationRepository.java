package com.oct.invoicesystem.domain.workflow.repository;

import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, UUID> {

    @Query("""
        SELECT d FROM ApprovalDelegation d
        WHERE d.departmentCode = :deptCode
          AND d.revoked = false
          AND d.fromDate <= :today
          AND d.toDate >= :today
    """)
    List<ApprovalDelegation> findActiveDelegationsForDepartment(String deptCode, LocalDate today);

    @Query("""
        SELECT d FROM ApprovalDelegation d
        WHERE d.delegatee.id = :delegateeId
          AND d.revoked = false
          AND d.fromDate <= :today
          AND d.toDate >= :today
    """)
    List<ApprovalDelegation> findActiveDelegationsForDelegatee(UUID delegateeId, LocalDate today);
}
