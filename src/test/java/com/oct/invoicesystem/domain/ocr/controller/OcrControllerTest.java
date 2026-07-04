package com.oct.invoicesystem.domain.ocr.controller;

import com.oct.invoicesystem.domain.ocr.dto.OcrExtractionResult;
import com.oct.invoicesystem.domain.ocr.service.OcrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies OCR extraction endpoint contract, notably that an empty (0-byte) upload
 * is rejected with 400 via {@code ValidationException} instead of NPE-ing into a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OcrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OcrService ocrService;

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void extract_EmptyFile_Returns400() throws Exception {
        // An empty multipart part must be rejected before reaching the service (was a 500).
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/ocr/extract")
                        .file("file", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(ocrService, never()).extract(any(), any());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE", username = "assistant")
    void extract_ValidFile_Returns200() throws Exception {
        byte[] content = "%PDF-1.4\nsample".getBytes(StandardCharsets.UTF_8);
        when(ocrService.extract(any(), eq("invoice.pdf")))
                .thenReturn(sampleResult());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/ocr/extract")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "invoice.pdf", "application/pdf", content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-1"));

        verify(ocrService).extract(any(), eq("invoice.pdf"));
    }

    private OcrExtractionResult sampleResult() {
        return OcrExtractionResult.builder()
                .invoiceNumber("INV-1")
                .build();
    }
}
