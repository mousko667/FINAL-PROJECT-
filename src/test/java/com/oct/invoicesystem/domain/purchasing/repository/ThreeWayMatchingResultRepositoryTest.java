package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ThreeWayMatchingResultRepositoryTest {

    @Autowired ThreeWayMatchingResultRepository repository;
    @Autowired EntityManager em;

    @Test
    void findLatestPerInvoice_noFilter_returnsPage() {
        Page<?> page = repository.findLatestPerInvoice(null, null, PageRequest.of(0, 20));
        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotNull();
    }

    @Test
    void findLatestPerInvoice_filterByStatus_returnsPage() {
        Page<?> page = repository.findLatestPerInvoice(MatchingStatus.MATCHED, null, PageRequest.of(0, 20));
        assertThat(page).isNotNull();
    }

    /**
     * Non-régression PROB-068 : avec au moins une ligne seedée, les prédicats {@code LIKE}
     * sur {@code :search} sont réellement évalués. Un appel sans terme (search=null) doit
     * retourner la ligne sans erreur ; un terme correspondant la retrouve ; un terme non
     * correspondant renvoie une page vide. (Sous H2 ; le bug Postgres {@code lower(bytea)} ne
     * se reproduit pas ici, mais ce test verrouille la requête NOT EXISTS + filtres contre
     * d'autres régressions — voir KNOWN_ISSUES_REGISTRY PROB-068.)
     */
    @Test
    void findLatestPerInvoice_withSeededRow_searchPredicatesEvaluated() {
        seedOneMatchingResult();

        Page<ThreeWayMatchingResult> noSearch =
                repository.findLatestPerInvoice(null, null, PageRequest.of(0, 20));
        assertThat(noSearch.getTotalElements()).isEqualTo(1);

        Page<ThreeWayMatchingResult> matchingSearch =
                repository.findLatestPerInvoice(null, "ACME", PageRequest.of(0, 20));
        assertThat(matchingSearch.getTotalElements()).isEqualTo(1);

        Page<ThreeWayMatchingResult> noMatchSearch =
                repository.findLatestPerInvoice(null, "ZZZNOMATCH", PageRequest.of(0, 20));
        assertThat(noMatchSearch.getTotalElements()).isZero();

        Page<ThreeWayMatchingResult> wrongStatus =
                repository.findLatestPerInvoice(MatchingStatus.MISMATCH, null, PageRequest.of(0, 20));
        assertThat(wrongStatus.getTotalElements()).isZero();
    }

    /** Seed minimal du graphe FK requis (user, department, supplier, invoice, PO, résultat). */
    private void seedOneMatchingResult() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();

        em.createNativeQuery("""
                INSERT INTO users (id, username, email, password_hash, first_name, last_name, is_active, created_at, updated_at)
                VALUES (?1, 'seed_user', 'seed@oct.test', 'x', 'Seed', 'User', TRUE, NOW(), NOW())
                """).setParameter(1, userId).executeUpdate();

        em.createNativeQuery("""
                INSERT INTO departments (id, code, name_fr, name_en, requires_n2, n1_role, is_active, created_at, updated_at)
                VALUES (?1, 'SEED', 'Dept', 'Dept', FALSE, 'ROLE_VALIDATEUR_N1_DG', TRUE, NOW(), NOW())
                """).setParameter(1, deptId).executeUpdate();

        em.createNativeQuery("""
                INSERT INTO suppliers (id, company_name, tax_id, contact_email, status, created_at, updated_at)
                VALUES (?1, 'ACME Corp', 'TAX-SEED', 'acme@oct.test', 'ACTIVE', NOW(), NOW())
                """).setParameter(1, supplierId).executeUpdate();

        em.createNativeQuery("""
                INSERT INTO invoices (id, reference_number, supplier_name, supplier_email, amount, currency,
                                      issue_date, due_date, status, data_sensitivity, version, department_id, submitted_by, created_at, updated_at)
                VALUES (?1, 'INV-SEED-1', 'ACME Corp', 'acme@oct.test', 100.00, 'XAF',
                        CURRENT_DATE, CURRENT_DATE, 'ARCHIVE', 'INTERNAL', 0, ?2, ?3, NOW(), NOW())
                """).setParameter(1, invoiceId).setParameter(2, deptId).setParameter(3, userId).executeUpdate();

        em.createNativeQuery("""
                INSERT INTO purchase_orders (id, po_number, supplier_id, total_amount, status, created_by, created_at, updated_at)
                VALUES (?1, 'PO-SEED-1', ?2, 100.00, 'OPEN', ?3, NOW(), NOW())
                """).setParameter(1, poId).setParameter(2, supplierId).setParameter(3, userId).executeUpdate();

        em.createNativeQuery("""
                INSERT INTO three_way_matching_results (id, invoice_id, purchase_order_id, status, created_at, updated_at)
                VALUES (?1, ?2, ?3, 'MATCHED', NOW(), NOW())
                """).setParameter(1, resultId).setParameter(2, invoiceId).setParameter(3, poId).executeUpdate();

        em.flush();
        em.clear();
    }
}
