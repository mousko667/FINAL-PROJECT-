package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingLineResolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThreeWayMatchingLineResolutionRepository extends JpaRepository<ThreeWayMatchingLineResolution, UUID> {
    List<ThreeWayMatchingLineResolution> findByInvoiceId(UUID invoiceId);
    Optional<ThreeWayMatchingLineResolution> findByInvoiceIdAndPoLineId(UUID invoiceId, UUID poLineId);
}
