package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceImportResultDTO;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for multi-invoice import (B8). NOT @Transactional: the import creates each
 * invoice in its own transaction (best-effort), so the test must really commit. Created invoices are
 * soft-deleted in {@link #cleanup()}.
 */
@SpringBootTest
@ActiveProfiles("test")
class InvoiceImportIntegrationTest {

    @Autowired private InvoiceImportService importService;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    private User actor;
    private final List<UUID> createdInvoiceIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        departmentRepository.findByCode("IMP").orElseGet(() -> {
            Department d = new Department();
            d.setCode("IMP"); d.setNameFr("Import"); d.setNameEn("Import");
            d.setN1Role("ROLE_MANAGER"); d.setActive(true); d.setRequiresN2(false);
            return departmentRepository.save(d);
        });
        Role role = roleRepository.findByName("ROLE_ASSISTANT_COMPTABLE").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_ASSISTANT_COMPTABLE"); return roleRepository.save(r);
        });
        actor = userRepository.findByUsername("import-aa").orElseGet(() -> {
            User u = userRepository.save(User.builder()
                    .username("import-aa").email("import-aa@oct.test").password("$2a$12$dummy")
                    .firstName("Import").lastName("AA").active(true).preferredLang("fr").build());
            u.getUserRoles().add(UserRole.builder().id(new UserRoleId(u.getId(), role.getId())).user(u).role(role).build());
            return userRepository.save(u);
        });
    }

    @AfterEach
    void cleanup() {
        for (UUID id : createdInvoiceIds) {
            invoiceRepository.findByIdAndDeletedAtIsNull(id)
                    .ifPresent(i -> { i.setDeletedAt(Instant.now()); invoiceRepository.save(i); });
        }
    }

    private void track(InvoiceImportResultDTO result) {
        result.results().stream().filter(InvoiceImportResultDTO.LineResult::success)
                .map(InvoiceImportResultDTO.LineResult::invoiceId).forEach(createdInvoiceIds::add);
    }

    @Test
    void csvImport_createsValidRows_andReportsInvalidPerLine() {
        String csv = """
                departmentCode,supplierName,supplierEmail,supplierTaxId,amount,currency,issueDate,dueDate,description
                IMP,Acme,acme@x.test,NIF1,1000,XAF,2026-06-01,2026-07-01,First
                IMP,Bad,bad@x.test,NIF2,1500,XAF,not-a-date,2026-07-01,Second
                """;
        MockMultipartFile file = new MockMultipartFile("file", "invoices.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        InvoiceImportResultDTO result = importService.importInvoices(file, null, actor.getId());
        track(result);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.results()).filteredOn(r -> r.line() == 2).singleElement()
                .satisfies(r -> assertThat(r.success()).isTrue());
        assertThat(result.results()).filteredOn(r -> r.line() == 3).singleElement()
                .satisfies(r -> assertThat(r.success()).isFalse());
    }

    /**
     * AUDIT-032 / AUDIT-033 (D4) : l'import de masse ne doit PAS contourner les invariants
     * financiers. C'est le point sensible du lot : l'import construit l'entite directement avec le
     * builder, sans passer par {@code InvoiceCreateRequest} ni par aucun {@code @Valid}. Les
     * annotations du DTO sont donc sans effet ici — seuls les invariants portes par
     * {@code InvoiceService.createInvoice} ferment ce chemin. Une liste blanche de devise
     * contournable par un fichier CSV ne vaudrait rien.
     *
     * <p>Les 4 lignes fautives reprennent les preuves runtime du finding : XOF (proscrite par la
     * regle projet), USD, montant nul, montant negatif. Seule la ligne conforme doit passer.</p>
     */
    @Test
    void csvImport_enforcesFinancialInvariants() {
        String csv = """
                departmentCode,supplierName,supplierEmail,supplierTaxId,amount,currency,issueDate,dueDate,description
                IMP,Conforme,ok@x.test,NIF0,1000,XAF,2026-06-01,2026-07-01,Valide
                IMP,DeviseXof,xof@x.test,NIF1,1000,XOF,2026-06-01,2026-07-01,Devise proscrite
                IMP,DeviseUsd,usd@x.test,NIF2,1000,USD,2026-06-01,2026-07-01,Devise hors perimetre
                IMP,MontantNul,zero@x.test,NIF3,0,XAF,2026-06-01,2026-07-01,Montant nul
                IMP,MontantNegatif,neg@x.test,NIF4,-50000,XAF,2026-06-01,2026-07-01,Montant negatif
                IMP,DatesIncoherentes,dates@x.test,NIF5,1000,XAF,2026-08-30,2026-07-01,Echeance avant emission
                """;
        MockMultipartFile file = new MockMultipartFile("file", "invoices.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        InvoiceImportResultDTO result = importService.importInvoices(file, null, actor.getId());
        track(result);

        assertThat(result.total()).isEqualTo(6);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(5);
        // Ligne 2 = la seule conforme ; lignes 3 a 7 = les cinq refus.
        assertThat(result.results()).filteredOn(r -> r.line() == 2).singleElement()
                .satisfies(r -> assertThat(r.success()).isTrue());
        assertThat(result.results()).filteredOn(r -> r.line() >= 3)
                .allSatisfy(r -> assertThat(r.success()).isFalse());
    }

    @Test
    void xmlImport_createsOneInvoicePerElement() {
        String xml = """
                <invoices>
                  <invoice><number>X1</number><date>2026-06-01</date><totalAmount>2000</totalAmount><supplierTaxId>NIFX1</supplierTaxId></invoice>
                  <invoice><number>X2</number><date>2026-06-02</date><totalAmount>3000</totalAmount><supplierTaxId>NIFX2</supplierTaxId></invoice>
                </invoices>
                """;
        MockMultipartFile file = new MockMultipartFile("file", "invoices.xml", "application/xml",
                xml.getBytes(StandardCharsets.UTF_8));

        InvoiceImportResultDTO result = importService.importInvoices(file, "IMP", actor.getId());
        track(result);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        Invoice first = invoiceRepository.findById(result.results().get(0).invoiceId()).orElseThrow();
        assertThat(first.getSupplierTaxId()).isEqualTo("NIFX1");
        assertThat(first.getAmount()).isEqualByComparingTo("2000");
    }
}
