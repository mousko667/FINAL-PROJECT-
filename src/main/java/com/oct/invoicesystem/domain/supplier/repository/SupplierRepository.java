package com.oct.invoicesystem.domain.supplier.repository;

import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Supplier> findByTaxIdAndDeletedAtIsNull(String taxId);

    // Uses native SQL ILIKE (PostgreSQL case-insensitive match) on the plain-text company_name column.
    // JPQL LOWER() was removed because PostgreSQL 18 rejects lower() on bytea-typed parameters
    // when the query also selects the encrypted bank_details column.
    // Each nullable param is wrapped in CAST(... AS text) so PostgreSQL can determine its type when
    // the value is null — a bare "(:p IS NULL OR ...)" leaves the bind untyped and raises
    // "could not determine data type of parameter $N" (PROB-038 / PROB-054 family).
    @Query(value = "SELECT * FROM suppliers WHERE deleted_at IS NULL " +
                   "AND (CAST(:name AS text) IS NULL OR company_name ILIKE CONCAT('%', CAST(:name AS text), '%')) " +
                   "AND (CAST(:taxId AS text) IS NULL OR tax_id = CAST(:taxId AS text)) " +
                   "AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text)) " +
                   "AND (CAST(:category AS text) IS NULL OR category = CAST(:category AS text))",
           countQuery = "SELECT COUNT(*) FROM suppliers WHERE deleted_at IS NULL " +
                        "AND (CAST(:name AS text) IS NULL OR company_name ILIKE CONCAT('%', CAST(:name AS text), '%')) " +
                        "AND (CAST(:taxId AS text) IS NULL OR tax_id = CAST(:taxId AS text)) " +
                        "AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text)) " +
                        "AND (CAST(:category AS text) IS NULL OR category = CAST(:category AS text))",
           nativeQuery = true)
    Page<Supplier> searchSuppliers(
            @Param("name") String name,
            @Param("taxId") String taxId,
            @Param("status") String status,
            @Param("category") String category,
            Pageable pageable);
            
    boolean existsByTaxIdAndDeletedAtIsNull(String taxId);
}
