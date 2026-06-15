package com.oct.invoicesystem.domain.supplier.repository;

import com.oct.invoicesystem.domain.supplier.model.SupplierContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierContractRepository extends JpaRepository<SupplierContract, UUID> {
    List<SupplierContract> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId);
}
