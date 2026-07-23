package com.oct.invoicesystem.domain.invoice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceUpdateRequest;
import com.oct.invoicesystem.domain.invoice.dto.DuplicateCheckDTO;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.response.PagedResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InvoiceService invoiceService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_AsAssistant_Returns201() throws Exception {
        UUID departmentId = UUID.randomUUID();
        InvoiceCreateRequest request = new InvoiceCreateRequest(
                departmentId, null, "ACME", "invoice@acme.com", "TAX-1", null,
                new BigDecimal("1000.00"), "XAF", LocalDate.now(), LocalDate.now().plusDays(30), "Test"
        );
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("assistant");
        user.setPassword("x");
        user.setActive(true);
        when(userRepository.findByUsername("assistant")).thenReturn(Optional.of(user));
        when(invoiceService.createInvoice(any(), any())).thenReturn(sampleInvoice());

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.referenceNumber").value("FAC-2026-00001"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUDIT-032 / AUDIT-033 (D4) : validation metier a la creation d'une facture.
    // Le systeme acceptait auparavant un montant nul ou negatif, une echeance anterieure a
    // l'emission, et n'importe quelle devise — dont XOF, proscrite par la regle projet et que la
    // migration V45 avait precisement servi a eliminer.
    // ──────────────────────────────────────────────────────────────────────────

    /** Construit une requete valide dont un seul aspect est ensuite altere par les tests. */
    private InvoiceCreateRequest validRequest(BigDecimal amount, String currency,
                                              LocalDate issueDate, LocalDate dueDate) {
        return new InvoiceCreateRequest(
                UUID.randomUUID(), null, "ACME", "invoice@acme.com", "TAX-1", null,
                amount, currency, issueDate, dueDate, "Test");
    }

    private void expectRejected(InvoiceCreateRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        // La requete ne doit jamais atteindre la couche service : elle est refusee a la validation.
        verify(invoiceService, never()).createInvoice(any(), any());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_RejectsZeroAmount() throws Exception {
        expectRejected(validRequest(BigDecimal.ZERO, "XAF", LocalDate.now(), LocalDate.now().plusDays(30)));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_RejectsNegativeAmount() throws Exception {
        expectRejected(validRequest(new BigDecimal("-50000"), "XAF", LocalDate.now(), LocalDate.now().plusDays(30)));
    }

    /**
     * Scenario exact du finding : emission 30/08, echeance 01/07 — deux mois AVANT.
     *
     * <p>L'assertion sur `$.errors` n'est pas cosmetique : la contrainte est portee par une
     * annotation de CLASSE, et `GlobalExceptionHandler` n'itere que sur `getFieldErrors()`. Si le
     * validateur cessait de rattacher la violation au noeud `dueDate` (`addPropertyNode`), elle
     * remonterait en `ObjectError` global et **disparaitrait silencieusement de la reponse** — le
     * statut resterait 400 et un test qui ne verifie que le statut resterait vert. C'est ce
     * contrat-la que ce test verrouille.</p>
     */
    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_RejectsDueDateBeforeIssueDate() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(
                                new BigDecimal("1000.00"), "XAF",
                                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 7, 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]",
                        org.hamcrest.Matchers.containsString("dueDate")));
        verify(invoiceService, never()).createInvoice(any(), any());
    }

    /** Une liste blanche insensible a la casse n'en serait pas une. */
    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_RejectsLowercaseXaf() throws Exception {
        expectRejected(validRequest(new BigDecimal("1000.00"), "xaf", LocalDate.now(), LocalDate.now().plusDays(30)));
    }

    /** Ni une liste blanche que des espaces suffiraient a contourner. */
    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_RejectsPaddedCurrency() throws Exception {
        expectRejected(validRequest(new BigDecimal("1000.00"), " XAF ", LocalDate.now(), LocalDate.now().plusDays(30)));
    }

    /** Les memes invariants valent a la mise a jour, pas seulement a la creation. */
    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void updateInvoice_RejectsInvalidAmountAndCurrency() throws Exception {
        InvoiceUpdateRequest negativeAmount = new InvoiceUpdateRequest(
                UUID.randomUUID(), null, "ACME", "invoice@acme.com", "TAX-1", null,
                new BigDecimal("-50000"), "XAF", LocalDate.now(), LocalDate.now().plusDays(30), "Test");
        mockMvc.perform(put("/api/v1/invoices/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeAmount)))
                .andExpect(status().isBadRequest());

        InvoiceUpdateRequest foreignCurrency = new InvoiceUpdateRequest(
                UUID.randomUUID(), null, "ACME", "invoice@acme.com", "TAX-1", null,
                new BigDecimal("1000.00"), "XOF", LocalDate.now(), LocalDate.now().plusDays(30), "Test");
        mockMvc.perform(put("/api/v1/invoices/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(foreignCurrency)))
                .andExpect(status().isBadRequest());

        verify(invoiceService, never()).updateInvoice(any(), any(), any());
    }

    /** Une echeance egale a l'emission reste licite : la contrainte est `dueDate >= issueDate`. */
    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_AcceptsDueDateEqualToIssueDate() throws Exception {
        LocalDate sameDay = LocalDate.of(2026, 8, 30);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("assistant");
        user.setPassword("x");
        user.setActive(true);
        when(userRepository.findByUsername("assistant")).thenReturn(Optional.of(user));
        when(invoiceService.createInvoice(any(), any())).thenReturn(sampleInvoice());

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validRequest(new BigDecimal("1000.00"), "XAF", sameDay, sameDay))))
                .andExpect(status().isCreated());
    }

    /** XOF est la devise BCEAO (Afrique de l'Ouest) : hors perimetre, et deja eliminee par V45. */
    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_RejectsXofCurrency() throws Exception {
        expectRejected(validRequest(new BigDecimal("1000.00"), "XOF", LocalDate.now(), LocalDate.now().plusDays(30)));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_RejectsUsdCurrency() throws Exception {
        expectRejected(validRequest(new BigDecimal("1000.00"), "USD", LocalDate.now(), LocalDate.now().plusDays(30)));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void createInvoice_RejectsEurCurrency() throws Exception {
        expectRejected(validRequest(new BigDecimal("1000.00"), "EUR", LocalDate.now(), LocalDate.now().plusDays(30)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInvoice_AsAdmin_Returns403() throws Exception {
        InvoiceCreateRequest request = new InvoiceCreateRequest(
                UUID.randomUUID(), null, "ACME", "invoice@acme.com", null, null,
                new BigDecimal("1000.00"), "XAF", LocalDate.now(), LocalDate.now().plusDays(30), null
        );
        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DAF", username = "daf")
    void listInvoices_AsAuthorizedRole_Returns200() throws Exception {
        // ADMIN is intentionally excluded from the invoice list (separation of duties:
        // admins manage the system, not financial data). DAF is an authorized financial role
        // and keeps the global (unscoped) view under the N9 department-scoping change.
        User daf = new User();
        daf.setId(UUID.randomUUID());
        daf.setUsername("daf");
        daf.setPassword("x");
        daf.setActive(true);
        when(userRepository.findByUsername("daf")).thenReturn(Optional.of(daf));
        PagedResponse<Invoice> response = new PagedResponse<>(List.of(sampleInvoice()), 0, 20, 1, 1, true);
        when(invoiceService.listInvoicesScoped(any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].referenceNumber").value("FAC-2026-00001"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listInvoices_AsAdmin_Returns403() throws Exception {
        // ADMIN must not access the financial invoice list (separation of duties).
        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void updateInvoice_AsAssistant_Returns200() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("assistant");
        user.setPassword("x");
        user.setActive(true);
        when(userRepository.findByUsername("assistant")).thenReturn(Optional.of(user));
        when(invoiceService.updateInvoice(any(), any(), any())).thenReturn(sampleInvoice());

        InvoiceUpdateRequest request = new InvoiceUpdateRequest(
                UUID.randomUUID(), null, "ACME", "invoice@acme.com", null, null,
                new BigDecimal("1000.00"), "XAF", LocalDate.now(), LocalDate.now().plusDays(30), "update"
        );
        mockMvc.perform(put("/api/v1/invoices/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void deleteInvoice_AsAssistant_Returns200() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("assistant");
        user.setPassword("x");
        user.setActive(true);
        when(userRepository.findByUsername("assistant")).thenReturn(Optional.of(user));
        doNothing().when(invoiceService).softDeleteInvoice(any(), any());

        mockMvc.perform(delete("/api/v1/invoices/{id}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void duplicateCheck_AsAssistant_UsesSuppliedId() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("assistant");
        user.setPassword("x");
        user.setActive(true);
        when(userRepository.findByUsername("assistant")).thenReturn(Optional.of(user));
        UUID supplierId = UUID.randomUUID();
        when(invoiceService.checkDuplicate(eq(supplierId), anyString()))
                .thenReturn(new DuplicateCheckDTO(true, 2L));

        mockMvc.perform(get("/api/v1/invoices/duplicate-check")
                        .param("supplierId", supplierId.toString())
                        .param("description", "Maintenance Q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true))
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    @WithMockUser(roles = "SUPPLIER", username = "supplier")
    void duplicateCheck_AsSupplier_IgnoresSuppliedIdAndUsesOwn() throws Exception {
        // IDOR guard: a supplier passing someone else's supplierId must be checked against
        // their OWN supplier id, never the query parameter.
        UUID ownSupplierId = UUID.randomUUID();
        UUID otherSupplierId = UUID.randomUUID();
        Supplier ownSupplier = Supplier.builder().id(ownSupplierId).build();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("supplier");
        user.setPassword("x");
        user.setActive(true);
        user.setSupplier(ownSupplier);
        when(userRepository.findByUsername("supplier")).thenReturn(Optional.of(user));
        when(invoiceService.checkDuplicate(any(), anyString()))
                .thenReturn(new DuplicateCheckDTO(false, 0L));

        mockMvc.perform(get("/api/v1/invoices/duplicate-check")
                        .param("supplierId", otherSupplierId.toString())
                        .param("description", "Probe"))
                .andExpect(status().isOk());

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(invoiceService).checkDuplicate(idCaptor.capture(), anyString());
        assertEquals(ownSupplierId, idCaptor.getValue());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateCheck_AsAdmin_Returns403() throws Exception {
        // ADMIN is not an invoice-entry role and must not reach this endpoint.
        mockMvc.perform(get("/api/v1/invoices/duplicate-check")
                        .param("supplierId", UUID.randomUUID().toString())
                        .param("description", "x"))
                .andExpect(status().isForbidden());
    }

    private Invoice sampleInvoice() {
        Department department = new Department();
        department.setId(UUID.randomUUID());
        User submitter = new User();
        submitter.setId(UUID.randomUUID());
        submitter.setUsername("assistant");
        submitter.setPassword("x");
        submitter.setActive(true);
        return Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00001")
                .department(department)
                .submittedBy(submitter)
                .supplierName("ACME")
                .supplierEmail("invoice@acme.com")
                .amount(new BigDecimal("1000.00"))
                .currency("XAF")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .status(InvoiceStatus.BROUILLON)
                .version(0)
                .build();
    }
}
