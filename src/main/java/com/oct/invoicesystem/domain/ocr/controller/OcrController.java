package com.oct.invoicesystem.domain.ocr.controller;

import com.oct.invoicesystem.domain.ocr.dto.OcrExtractionResult;
import com.oct.invoicesystem.domain.ocr.service.OcrService;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
@Tag(name = "OCR", description = "Invoice field extraction from uploaded files")
public class OcrController {

    private final OcrService ocrService;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPPLIER', 'ASSISTANT_COMPTABLE')")
    @Operation(
        summary = "Extract invoice fields via OCR",
        description = "Accepts a PDF, JPEG, PNG, or TIFF invoice file and returns extracted fields "
            + "(invoice number, date, total, line items, PO reference). "
            + "The supplier reviews and corrects these before final submission."
    )
    public ResponseEntity<ApiResponse<OcrExtractionResult>> extract(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new ValidationException("error.ocr.empty_file");
        }
        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        OcrExtractionResult result = ocrService.extract(bytes, filename);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
