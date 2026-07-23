package com.oct.invoicesystem.domain.invoice.controller;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.service.InvoiceDocumentService;
import com.oct.invoicesystem.domain.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceDocumentService invoiceDocumentService;

    /** AUDIT-007/018: the controller now asks the invoice service to enforce the invoice's own scope. */
    @MockBean
    private com.oct.invoicesystem.domain.invoice.service.InvoiceService invoiceService;

    @MockBean
    private com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void upload_AsAssistant_Returns201() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceDocumentService.upload(eq(invoiceId), any(MultipartFile.class), eq("assistant")))
                .thenReturn(sampleDocument(invoiceId, UUID.randomUUID()));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/invoices/{invoiceId}/documents", invoiceId)
                        .file("file", "%PDF-1.4\nsample".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileType").value("application/pdf"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void upload_AsAdmin_Returns403() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/invoices/{invoiceId}/documents", invoiceId)
                        .file("file", "%PDF-1.4\nsample".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_AsAdmin_Returns403() throws Exception {
        // AUDIT-007/018: the admin has no financial access — an invoice's attachments follow the
        // invoice's own rule, and GET /invoices/{id} already denies the admin.
        UUID invoiceId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents", invoiceId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VALIDATEUR_N1_DRH", username = "drh")
    void list_AsValidatorOfAnotherDepartment_Returns403() throws Exception {
        // AUDIT-007: the invoice is denied to this validator, so its documents must be too.
        UUID invoiceId = UUID.randomUUID();
        when(invoiceService.getByIdScoped(eq(invoiceId), any()))
                .thenThrow(new AccessDeniedException("error.access_denied"));

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents", invoiceId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void list_AsAssistantComptable_Returns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceDocumentService.listByInvoice(invoiceId))
                .thenReturn(List.of(sampleDocument(invoiceId, UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].originalFilename").value("invoice.pdf"));
    }

    @Test
    @WithMockUser(roles = "SUPPLIER", username = "supplier")
    void list_AsSupplier_Returns403() throws Exception {
        // IDOR fix (MAJEUR-1): generic staff-facing document list must not be reachable by SUPPLIER.
        // Suppliers have their own guarded portal routes (SupplierPortalController + ensureOwnInvoice).
        UUID invoiceId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents", invoiceId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void download_AsAdmin_Returns403() throws Exception {
        // AUDIT-018: the download used to hand the admin a 15-min pre-signed MinIO URL to the
        // invoice PDF. No pre-signed URL may ever be minted for a role without financial access.
        UUID invoiceId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents/{docId}/download", invoiceId, docId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VALIDATEUR_N1_DRH", username = "drh")
    void download_AsValidatorOfAnotherDepartment_Returns403() throws Exception {
        // AUDIT-007: scope is checked BEFORE the pre-signed URL is generated.
        UUID invoiceId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(invoiceService.getByIdScoped(eq(invoiceId), any()))
                .thenThrow(new AccessDeniedException("error.access_denied"));

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents/{docId}/download", invoiceId, docId))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verify(invoiceDocumentService, org.mockito.Mockito.never())
                .generateDownloadUrlAndLog(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void download_AsAssistantComptable_Returns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(invoiceDocumentService.generateDownloadUrlAndLog(eq(invoiceId), eq(docId), any(), any(), any()))
                .thenReturn("http://localhost:9000/mock-url");

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents/{docId}/download", invoiceId, docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("http://localhost:9000/mock-url"));
    }

    @Test
    @WithMockUser(roles = "SUPPLIER", username = "supplier")
    void download_AsSupplier_Returns403() throws Exception {
        // IDOR fix (MAJEUR-1): generic staff-facing download must not be reachable by SUPPLIER.
        UUID invoiceId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents/{docId}/download", invoiceId, docId))
                .andExpect(status().isForbidden());
    }

    private InvoiceDocument sampleDocument(UUID invoiceId, UUID uploadedById) {
        Invoice invoice = Invoice.builder().id(invoiceId).build();
        User uploader = new User();
        uploader.setId(uploadedById);
        uploader.setUsername("assistant");
        uploader.setPassword("x");
        uploader.setActive(true);
        return InvoiceDocument.builder()
                .id(UUID.randomUUID())
                .invoice(invoice)
                .originalFilename("invoice.pdf")
                .fileType("application/pdf")
                .fileSizeBytes(100L)
                .checksumSha256("abc")
                .uploadedBy(uploader)
                .uploadedAt(Instant.now())
                .build();
    }
}
