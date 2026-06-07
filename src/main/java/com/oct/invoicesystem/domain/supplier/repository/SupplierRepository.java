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
    @Query(value = "SELECT * FROM suppliers WHERE deleted_at IS NULL " +
                   "AND (:name IS NULL OR company_name ILIKE CONCAT('%', :name, '%')) " +
                   "AND (:taxId IS NULL OR tax_id = :taxId) " +
                   "AND (:status IS NULL OR status = :status)",
           countQuery = "SELECT COUNT(*) FROM suppliers WHERE deleted_at IS NULL " +
                        "AND (:name IS NULL OR company_name ILIKE CONCAT('%', :name, '%')) " +
                        "AND (:taxId IS NULL OR tax_id = :taxId) " +
                        "AND (:status IS NULL OR status = :status)",
           nativeQuery = true)
    Page<Supplier> searchSuppliers(
            @Param("name") String name,
            @Param("taxId") String taxId,
            @Param("status") String status,
            Pageable pageable);
            
    boolean existsByTaxIdAndDeletedAtIsNull(String taxId);
}
