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
    void list_AsAdmin_Returns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceDocumentService.listByInvoice(invoiceId))
                .thenReturn(List.of(sampleDocument(invoiceId, UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].originalFilename").value("invoice.pdf"));
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
    void download_AsAdmin_Returns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        // P11-50: download now records an access-log entry via generateDownloadUrlAndLog.
        when(invoiceDocumentService.generateDownloadUrlAndLog(eq(invoiceId), eq(docId), any(), any(), any()))
                .thenReturn("http://localhost:9000/mock-url");

        mockMvc.perform(get("/api/v1/invoices/{invoiceId}/documents/{docId}/download", invoiceId, docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("http://localhost:9000/mock-url"));
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
