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

    @Query("SELECT s FROM Supplier s WHERE s.deletedAt IS NULL " +
           "AND (:name IS NULL OR LOWER(s.companyName) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:taxId IS NULL OR s.taxId = :taxId) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<Supplier> searchSuppliers(
            @Param("name") String name,
            @Param("taxId") String taxId,
            @Param("status") SupplierStatus status,
            Pageable pageable);
            
    boolean existsByTaxIdAndDeletedAtIsNull(String taxId);
}
