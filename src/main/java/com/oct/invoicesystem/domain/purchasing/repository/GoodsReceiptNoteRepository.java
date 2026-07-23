package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptNote;
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
public interface GoodsReceiptNoteRepository extends JpaRepository<GoodsReceiptNote, UUID> {

    Optional<GoodsReceiptNote> findByGrnNumber(String grnNumber);

    @Query("SELECT grn FROM GoodsReceiptNote grn WHERE grn.purchaseOrder.id = ?1 AND grn.deletedAt IS NULL")
    List<GoodsReceiptNote> findByPurchaseOrderId(UUID purchaseOrderId);

    /**
     * Liste paginee de tous les bons de reception non supprimes (AUDIT-028).
     *
     * <p>Tri porte par la requete : la page « Bons de Reception » presente les receptions les plus
     * recentes en premier. {@code receiptDate} etant un {@link java.time.LocalDate}, les receptions
     * d'une meme journee n'auraient aucun ordre determine entre elles — d'ou le depart par
     * {@code createdAt}, sans lequel la pagination serait instable d'une page a l'autre.</p>
     *
     * <p>{@code @EntityGraph} evite un N+1 : {@code toDTO} traverse les items, le bon de commande
     * et l'utilisateur receptionnaire, tous LAZY. Sans lui, une page de N bons declencherait
     * 1 + N x (3 + M) requetes (M = lignes par bon). Meme parti que
     * {@link PurchaseOrderRepository#findAllActive}.</p>
     */
    @EntityGraph(attributePaths = {"items", "items.purchaseOrderItem", "purchaseOrder", "receivedBy"})
    @Query(value = "SELECT grn FROM GoodsReceiptNote grn WHERE grn.deletedAt IS NULL "
                 + "ORDER BY grn.receiptDate DESC, grn.createdAt DESC",
           countQuery = "SELECT COUNT(grn) FROM GoodsReceiptNote grn WHERE grn.deletedAt IS NULL")
    Page<GoodsReceiptNote> findAllActive(Pageable pageable);
}
