package com.oct.invoicesystem.domain.invoice.repository;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByReferenceNumber(String referenceNumber);

    /**
     * Duplicate detection: checks whether an invoice with the same supplier AND the same
     * description (used as invoice number when submitted via supplier portal) already exists
     * in a non-rejected, non-archived state within the last 365 days.
     */
    @Query("""
            SELECT COUNT(i) FROM Invoice i
            WHERE i.deletedAt IS NULL
              AND i.supplier.id = :supplierId
              AND LOWER(i.description) = LOWER(:description)
              AND i.status NOT IN ('REJETE', 'ARCHIVE')
              AND i.createdAt >= :since
            """)
    long countDuplicatesBySupplierAndDescription(
            @Param("supplierId") UUID supplierId,
            @Param("description") String description,
            @Param("since") java.time.Instant since
    );

    Optional<Invoice> findByIdAndDeletedAtIsNull(UUID id);

    // Every nullable parameter is wrapped in an explicit CAST so PostgreSQL can determine its type
    // even when the value is null. Without it, a bare "(:param IS NULL OR ...)" leaves the bind
    // parameter untyped and PostgreSQL raises "could not determine data type of parameter $N"
    // (SQLGrammarException) — exactly the cash-flow 500 fixed in PROB-054 (same family as PROB-038).
    @Query("""
            SELECT i
            FROM Invoice i
            WHERE i.deletedAt IS NULL
              AND (CAST(:status AS string) IS NULL OR i.status = :status)
              AND (CAST(:departmentId AS uuid) IS NULL OR i.department.id = :departmentId)
              AND (CAST(:fromDate AS date) IS NULL OR i.issueDate >= :fromDate)
              AND (CAST(:toDate AS date) IS NULL OR i.issueDate <= :toDate)
              AND (:reference IS NULL OR LOWER(i.referenceNumber) LIKE LOWER(CONCAT('%', CAST(:reference AS string), '%')))
              AND (CAST(:supplierId AS uuid) IS NULL OR (i.supplier IS NOT NULL AND i.supplier.id = :supplierId))
            """)
    Page<Invoice> findAllWithFilters(
            @Param("status") InvoiceStatus status,
            @Param("departmentId") UUID departmentId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("reference") String reference,
            @Param("supplierId") UUID supplierId,
            Pageable pageable
    );

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.deletedAt IS NULL AND i.dueDate < :today AND i.status NOT IN ('PAYE', 'ARCHIVE')")
    long countOverdueInvoices(@Param("today") LocalDate today);

    @Query("SELECT i FROM Invoice i WHERE i.deletedAt IS NULL AND i.dueDate < :today AND i.status NOT IN ('PAYE', 'ARCHIVE')")
    List<Invoice> findOverdueInvoices(@Param("today") LocalDate today);

    @Query("""
            SELECT i
            FROM Invoice i
            WHERE i.deletedAt IS NULL
              AND i.status IN ('EN_VALIDATION_N1', 'EN_VALIDATION_N2')
            """)
    Page<Invoice> findPendingValidationQueue(Pageable pageable);

    @Query("SELECT i.supplierName, SUM(i.amount) FROM Invoice i WHERE i.deletedAt IS NULL GROUP BY i.supplierName ORDER BY SUM(i.amount) DESC")
    Page<Object[]> findTopSuppliersByAmount(Pageable pageable);

    @Query("SELECT i.status, COUNT(i) FROM Invoice i WHERE i.deletedAt IS NULL GROUP BY i.status")
    List<Object[]> countInvoicesByStatus();

    @Query("SELECT i.status, COUNT(i) FROM Invoice i WHERE i.deletedAt IS NULL AND i.supplier IS NOT NULL AND i.supplier.id = :supplierId GROUP BY i.status")
    List<Object[]> countInvoicesByStatusForSupplier(@Param("supplierId") UUID supplierId);

    @Query("SELECT i.matchingStatus, COUNT(i) FROM Invoice i WHERE i.deletedAt IS NULL AND i.supplier IS NOT NULL AND i.supplier.id = :supplierId GROUP BY i.matchingStatus")
    List<Object[]> countInvoicesByMatchingStatusForSupplier(@Param("supplierId") UUID supplierId);

    @Query("SELECT MIN(i.dueDate) FROM Invoice i WHERE i.deletedAt IS NULL AND i.supplier IS NOT NULL AND i.supplier.id = :supplierId AND i.status = 'BON_A_PAYER' AND NOT EXISTS (SELECT p FROM Payment p WHERE p.invoice = i AND p.deleted = false)")
    java.time.LocalDate findNextExpectedPaymentDateForSupplier(@Param("supplierId") UUID supplierId);

    // Native SQL with ILIKE + explicit ::text / ::uuid / ::timestamptz casts on the nullable params.
    // JPQL LOWER(CONCAT('%', :keyword, '%')) was rejected by PostgreSQL ("function lower(bytea) does
    // not exist") because a null String param is inferred as bytea — the same issue already fixed for
    // SupplierRepository.searchSuppliers. Casting each param makes its type explicit even when null.
    @Query(value = """
            SELECT * FROM invoices i
            WHERE i.deleted_at IS NULL
              AND i.status = 'ARCHIVE'
              AND (CAST(:keyword AS text) IS NULL
                   OR i.reference_number ILIKE CONCAT('%', CAST(:keyword AS text), '%')
                   OR i.supplier_name ILIKE CONCAT('%', CAST(:keyword AS text), '%')
                   OR i.description ILIKE CONCAT('%', CAST(:keyword AS text), '%'))
              AND (CAST(:department AS uuid) IS NULL OR i.department_id = CAST(:department AS uuid))
              AND (CAST(:from AS timestamptz) IS NULL OR i.created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR i.created_at <= CAST(:to AS timestamptz))
            ORDER BY i.created_at DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM invoices i
            WHERE i.deleted_at IS NULL
              AND i.status = 'ARCHIVE'
              AND (CAST(:keyword AS text) IS NULL
                   OR i.reference_number ILIKE CONCAT('%', CAST(:keyword AS text), '%')
                   OR i.supplier_name ILIKE CONCAT('%', CAST(:keyword AS text), '%')
                   OR i.description ILIKE CONCAT('%', CAST(:keyword AS text), '%'))
              AND (CAST(:department AS uuid) IS NULL OR i.department_id = CAST(:department AS uuid))
              AND (CAST(:from AS timestamptz) IS NULL OR i.created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR i.created_at <= CAST(:to AS timestamptz))
            """,
           nativeQuery = true)
    Page<Invoice> searchArchived(
            @Param("keyword") String keyword,
            @Param("department") UUID department,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    // P11-53: count invoices carrying (encrypted-at-rest) supplier bank details.
    long countBySupplierBankDetailsIsNotNull();
}
