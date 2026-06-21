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
     * <p>Le {@code CAST(:status AS string)} neutralise le bug Postgres d'inférence
     * de type sur un paramètre enum nullable (PROB-038/054).</p>
     *
     * @param status  statut de rapprochement (nullable — ignoré si null)
     * @param search  terme de recherche sur référence, fournisseur, numéro PO (nullable)
     * @param pageable pagination et tri
     * @return page de résultats
     */
    @Query("""
            SELECT r FROM ThreeWayMatchingResult r
            WHERE r.createdAt = (
                SELECT MAX(r2.createdAt) FROM ThreeWayMatchingResult r2
                WHERE r2.invoice.id = r.invoice.id)
              AND (CAST(:status AS string) IS NULL OR r.status = :status)
              AND (:search IS NULL
                   OR LOWER(r.invoice.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(r.invoice.supplierName)    LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(r.purchaseOrder.poNumber)  LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY r.createdAt DESC
            """)
    Page<ThreeWayMatchingResult> findLatestPerInvoice(@Param("status") MatchingStatus status,
                                                      @Param("search") String search,
                                                      Pageable pageable);
}
