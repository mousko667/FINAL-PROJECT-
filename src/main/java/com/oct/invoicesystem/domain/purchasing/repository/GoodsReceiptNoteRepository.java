package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoodsReceiptNoteRepository extends JpaRepository<GoodsReceiptNote, UUID> {

    Optional<GoodsReceiptNote> findByGrnNumber(String grnNumber);

    @Query("SELECT grn FROM GoodsReceiptNote grn WHERE grn.purchaseOrder.id = ?1 AND grn.deletedAt IS NULL")
    List<GoodsReceiptNote> findByPurchaseOrderId(UUID purchaseOrderId);
}
