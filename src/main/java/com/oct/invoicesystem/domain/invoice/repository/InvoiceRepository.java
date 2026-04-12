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
import java.util.List;
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
              AND (:reference IS NULL OR LOWER(i.referenceNumber) LIKE LOWER(CONCAT('%', CAST(:reference AS string), '%')))
            """)
    Page<Invoice> findAllWithFilters(
            @Param("status") InvoiceStatus status,
            @Param("departmentId") UUID departmentId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("reference") String reference,
            Pageable pageable
    );

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.deletedAt IS NULL AND i.dueDate < :today AND i.status NOT IN ('PAYE', 'ARCHIVE')")
    long countOverdueInvoices(@Param("today") LocalDate today);

    @Query("SELECT i.supplierName, SUM(i.amount) FROM Invoice i WHERE i.deletedAt IS NULL GROUP BY i.supplierName ORDER BY SUM(i.amount) DESC")
    Page<Object[]> findTopSuppliersByAmount(Pageable pageable);

    @Query("SELECT i.status, COUNT(i) FROM Invoice i WHERE i.deletedAt IS NULL GROUP BY i.status")
    List<Object[]> countInvoicesByStatus();
}
