package com.oct.invoicesystem.domain.checklist.repository;

import com.oct.invoicesystem.domain.checklist.model.ChecklistResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistResponseRepository extends JpaRepository<ChecklistResponse, UUID> {

    Optional<ChecklistResponse> findFirstByInvoiceIdOrderByRespondedAtDesc(UUID invoiceId);
}
