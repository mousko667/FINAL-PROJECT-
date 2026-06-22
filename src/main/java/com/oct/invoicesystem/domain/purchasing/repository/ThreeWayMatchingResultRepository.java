package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThreeWayMatchingResultRepository extends JpaRepository<ThreeWayMatchingResult, UUID> {

    Optional<ThreeWayMatchingResult> findTopByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    default Optional<ThreeWayMatchingResult> findByInvoiceId(UUID invoiceId) {
        return findTopByInvoiceIdOrderByCreatedAtDesc(invoiceId);
    }

    /**
     * Retourne la page des derniers résultats de rapprochement (un par facture),
     * avec filtres optionnels sur le statut et un terme de recherche.
     *
     * <p>Les {@code CAST(:status AS string)} et {@code CAST(:search AS string)}
     * neutralisent le bug Postgres d'inférence de type sur un paramètre nullable
     * dans une clause {@code (:param IS NULL OR ...)} (PROB-038/054/068). Sans le CAST
     * sur {@code search}, un appel sans terme de recherche échoue en 500
     * (SQLGrammarException) en runtime, alors que les tests sur base vide passent.</p>
     *
     * <p>L'unicité d'une ligne par facture est garantie même en cas d'horodatage
     * {@code createdAt} identique : on retient le résultat sans aucun autre plus récent,
     * et, à {@code createdAt} égal, celui dont l'{@code id} est le plus grand (départage
     * déterministe — évite les doublons de facture dans la page).</p>
     *
     * @param status  statut de rapprochement (nullable — ignoré si null)
     * @param search  terme de recherche sur référence, fournisseur, numéro PO (nullable)
     * @param pageable pagination et tri
     * @return page de résultats
     */
    @Query("""
            SELECT r FROM ThreeWayMatchingResult r
            WHERE NOT EXISTS (
                SELECT 1 FROM ThreeWayMatchingResult r2
                WHERE r2.invoice.id = r.invoice.id
                  AND (r2.createdAt > r.createdAt
                       OR (r2.createdAt = r.createdAt AND r2.id > r.id)))
              AND (CAST(:status AS string) IS NULL OR r.status = :status)
              AND (CAST(:search AS string) IS NULL
                   OR LOWER(r.invoice.referenceNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(r.invoice.supplierName)    LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(r.purchaseOrder.poNumber)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            ORDER BY r.createdAt DESC
            """)
    Page<ThreeWayMatchingResult> findLatestPerInvoice(@Param("status") MatchingStatus status,
                                                      @Param("search") String search,
                                                      Pageable pageable);
}
