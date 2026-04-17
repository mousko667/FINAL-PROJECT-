package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThreeWayMatchingResultRepository extends JpaRepository<ThreeWayMatchingResult, UUID> {

    Optional<ThreeWayMatchingResult> findByInvoiceId(UUID invoiceId);
}
