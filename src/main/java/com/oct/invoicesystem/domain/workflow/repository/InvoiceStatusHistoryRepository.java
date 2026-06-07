package com.oct.invoicesystem.domain.workflow.repository;

import com.oct.invoicesystem.domain.workflow.dto.InvoiceHistoryDTO;
import com.oct.invoicesystem.domain.workflow.model.InvoiceStatusHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface InvoiceStatusHistoryRepository extends JpaRepository<InvoiceStatusHistory, UUID> {
    long countByInvoiceId(UUID invoiceId);

    @Query("SELECT COUNT(DISTINCT h.invoice.id) FROM InvoiceStatusHistory h WHERE h.toStatus = :status")
    long countUniqueInvoicesByToStatus(@Param("status") String status);

    @Query("SELECT h FROM InvoiceStatusHistory h WHERE h.toStatus IN ('SOUMIS', 'BON_A_PAYER') ORDER BY h.invoice.id, h.changedAt ASC")
    List<InvoiceStatusHistory> findRelevantHistoryForProcessingTime();

    List<InvoiceStatusHistory> findByInvoiceIdOrderByChangedAtAsc(UUID invoiceId);

    // DTO projection avoids LazyInitializationException — joins invoice and user inside the query
    @Query("SELECT new com.oct.invoicesystem.domain.workflow.dto.InvoiceHistoryDTO(" +
           "h.id, i.id, i.referenceNumber, h.fromStatus, h.toStatus, " +
           "CASE WHEN h.changedBy IS NOT NULL THEN h.changedBy.id ELSE NULL END, " +
           "CASE WHEN h.changedBy IS NOT NULL THEN h.changedBy.username ELSE NULL END, " +
           "h.changeReason, h.changedAt) " +
           "FROM InvoiceStatusHistory h JOIN h.invoice i " +
           "WHERE i.id = :invoiceId ORDER BY h.changedAt ASC")
    List<InvoiceHistoryDTO> findHistoryDTOsByInvoiceId(@Param("invoiceId") UUID invoiceId);

    List<InvoiceStatusHistory> findAllByOrderByChangedAtDesc(Pageable pageable);
}
