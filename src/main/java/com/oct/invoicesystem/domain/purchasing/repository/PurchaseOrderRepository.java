package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT po FROM PurchaseOrder po WHERE po.supplier.id = ?1 AND po.deletedAt IS NULL")
    List<PurchaseOrder> findBySupplierId(UUID supplierId);

    @Query("SELECT po FROM PurchaseOrder po LEFT JOIN FETCH po.items WHERE po.id = ?1 AND po.deletedAt IS NULL")
    Optional<PurchaseOrder> findByIdActive(UUID id);

    /**
     * Purchase orders a supplier may still invoice against: their own, not soft-deleted, and still
     * {@code OPEN} ({@code CLOSED}/{@code CANCELLED} cannot be referenced on a new invoice).
     * Backs the supplier portal's PO selector (audit finding AUDIT-001); the supplier scope is what
     * keeps it from leaking another supplier's orders.
     */
    @EntityGraph(attributePaths = "items")
    @Query("SELECT po FROM PurchaseOrder po WHERE po.supplier.id = ?1 AND po.deletedAt IS NULL "
            + "AND po.status = com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderStatus.OPEN "
            + "ORDER BY po.createdAt DESC")
    List<PurchaseOrder> findOpenBySupplierId(UUID supplierId);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT po FROM PurchaseOrder po WHERE po.deletedAt IS NULL")
    Page<PurchaseOrder> findAllActive(Pageable pageable);
}
