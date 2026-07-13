package com.oct.invoicesystem.domain.invoice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceControllerExportPdfTest {

    @Autowired MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;

    @Test
    @WithMockUser(username = "daf", roles = {"DAF"})
    void exportPdfReturnsPdfWithFrenchTitle() throws Exception {
        com.oct.invoicesystem.domain.user.model.User mockUser = new com.oct.invoicesystem.domain.user.model.User();
        mockUser.setFirstName("Marie");
        mockUser.setLastName("Dubois");
        org.mockito.Mockito.when(securityHelper.currentUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(mockUser);

        byte[] body = mockMvc.perform(get("/api/v1/invoices/export")
                        .param("format", "pdf")
                        .header("Accept-Language", "fr")
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        // Valid PDF
        org.assertj.core.api.Assertions.assertThat(
                new String(body, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        // French title + signature block present
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(body)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            org.assertj.core.api.Assertions.assertThat(text).contains("Factures");
            org.assertj.core.api.Assertions.assertThat(text).contains("Signature");
        }
    }
}
