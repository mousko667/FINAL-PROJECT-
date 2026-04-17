package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @Query("SELECT po FROM PurchaseOrder po WHERE po.supplier.id = ?1 AND po.deletedAt IS NULL")
    List<PurchaseOrder> findBySupplierId(UUID supplierId);

    @Query("SELECT po FROM PurchaseOrder po WHERE po.id = ?1 AND po.deletedAt IS NULL")
    Optional<PurchaseOrder> findByIdActive(UUID id);
}
