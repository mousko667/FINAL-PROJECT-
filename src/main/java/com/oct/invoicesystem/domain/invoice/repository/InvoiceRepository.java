package com.oct.invoicesystem.domain.invoice.repository;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByReferenceNumber(String referenceNumber);

    Optional<Invoice> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            SELECT i
            FROM Invoice i
            WHERE i.deletedAt IS NULL
              AND (:status IS NULL OR i.status = :status)
              AND (:departmentId IS NULL OR i.department.id = :departmentId)
              AND (:fromDate IS NULL OR i.issueDate >= :fromDate)
              AND (:toDate IS NULL OR i.issueDate <= :toDate)
              AND (:reference IS NULL OR LOWER(i.referenceNumber) LIKE LOWER(CONCAT('%', :reference, '%')))
            """)
    Page<Invoice> findAllWithFilters(
            @Param("status") InvoiceStatus status,
            @Param("departmentId") UUID departmentId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("reference") String reference,
            Pageable pageable
    );
}
