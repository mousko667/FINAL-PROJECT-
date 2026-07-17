package com.oct.invoicesystem.domain.supplier.controller;

import com.oct.invoicesystem.domain.report.dto.SupplierPerformanceDTO;
import com.oct.invoicesystem.domain.report.service.ReportService;
import com.oct.invoicesystem.domain.supplier.dto.SupplierCreateRequest;
import com.oct.invoicesystem.domain.supplier.dto.SupplierResponse;
import com.oct.invoicesystem.domain.supplier.dto.SupplierUpdateRequest;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocument;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocumentType;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.supplier.service.SupplierService;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
@Slf4j
public class SupplierController {

    private final SupplierService supplierService;
    private final MinioStorageService minioStorageService;
    private final ReportService reportService;
    private final SecurityHelper securityHelper;
    private final com.oct.invoicesystem.shared.export.TabularExportService tabularExportService;
    private final org.springframework.context.MessageSource messageSource;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ApiResponse<SupplierResponse> createSupplier(@Valid @RequestBody SupplierCreateRequest request) {
        return ApiResponse.success(supplierService.createSupplier(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ApiResponse<SupplierResponse> updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierUpdateRequest request) {
        return ApiResponse.success(supplierService.updateSupplier(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')")
    public ApiResponse<SupplierResponse> getSupplier(@PathVariable UUID id) {
        return ApiResponse.success(supplierService.getSupplier(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')")
    public ApiResponse<PagedResponse<SupplierResponse>> searchSuppliers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String taxId,
            @RequestParam(required = false) SupplierStatus status,
            @RequestParam(required = false) com.oct.invoicesystem.domain.supplier.model.SupplierCategory category,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
            Pageable pageable) {
        Page<SupplierResponse> page = supplierService.searchSuppliers(name, taxId, status, category, from, to, pageable);
        return ApiResponse.success(PagedResponse.of(page));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')")
    public ResponseEntity<byte[]> exportSuppliers(@RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
            Authentication authentication) {
        var fmt = com.oct.invoicesystem.shared.export.TabularExportService.Format.from(format);
        // Force ASC sort by createdAt for export if possible, but unpaged is used here. 
        // We'll pass Sort.by("createdAt").ascending() just in case.
        var suppliers = supplierService.searchSuppliers(null, null, null, null, from, to,
                org.springframework.data.domain.PageRequest.of(0, 10000, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "createdAt"))).getContent();
        java.util.Locale locale = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        java.util.List<String> headers = java.util.List.of(
                messageSource.getMessage("report.excel.header.company", null, locale),
                messageSource.getMessage("report.excel.header.tax_id", null, locale),
                messageSource.getMessage("report.excel.header.email", null, locale),
                messageSource.getMessage("report.excel.header.phone", null, locale),
                messageSource.getMessage("report.excel.header.address", null, locale),
                messageSource.getMessage("report.excel.header.status", null, locale),
                messageSource.getMessage("report.excel.header.category", null, locale)
        );
        java.util.List<java.util.List<String>> rows = suppliers.stream().map(s -> java.util.List.of(
                ns(s.companyName()), ns(s.taxId()), ns(s.contactEmail()), ns(s.contactPhone()),
                ns(s.address()), s.status() == null ? "" : s.status().name(),
                s.category() == null ? "" : s.category().name())).toList();
        String title = messageSource.getMessage("export.title.suppliers", null, locale);
        
        String periodLabel = null;
        if (from != null || to != null) {
            String fromStr = from != null ? java.time.LocalDate.ofInstant(from, java.time.ZoneId.systemDefault()).toString() : "...";
            String toStr = to != null ? java.time.LocalDate.ofInstant(to, java.time.ZoneId.systemDefault()).toString() : "...";
            periodLabel = messageSource.getMessage("export.pdf.period", new Object[]{fromStr, toStr}, locale);
        }
        
        com.oct.invoicesystem.shared.export.ReportMetadata meta =
                com.oct.invoicesystem.shared.export.ReportMetadata.of(securityHelper.currentUser(authentication), messageSource, periodLabel, null, locale);
        byte[] body = tabularExportService.export(fmt, title, headers, rows, meta, messageSource);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=suppliers_export." + fmt.extension)
                .contentType(org.springframework.http.MediaType.parseMediaType(fmt.mediaType))
                .body(body);
    }

    private static String ns(String s) { return s == null ? "" : s; }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ApiResponse<Void> activateSupplier(@PathVariable UUID id, Authentication authentication) {
        log.info("Supplier {} activated by {}", id, authentication != null ? authentication.getName() : "unknown");
        supplierService.activateSupplier(id, securityHelper.currentUser(authentication));
        return ApiResponse.success(null, "supplier.activated.success");
    }

    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ApiResponse<Void> suspendSupplier(@PathVariable UUID id, Authentication authentication) {
        log.info("Supplier {} suspended by {}", id, authentication != null ? authentication.getName() : "unknown");
        supplierService.suspendSupplier(id);
        return ApiResponse.success(null, "supplier.suspended.success");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void softDeleteSupplier(@PathVariable UUID id) {
        supplierService.softDeleteSupplier(id);
    }

    @GetMapping("/{id}/performance")
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF')")
    public ApiResponse<SupplierPerformanceDTO> getPerformanceMetrics(@PathVariable UUID id) {
        supplierService.getSupplier(id); // validates existence -> 404 if missing
        return ApiResponse.success(reportService.getSupplierPerformance(id));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')")
    public ApiResponse<List<Map<String, Object>>> listSupplierDocuments(@PathVariable UUID id) {
        supplierService.getSupplier(id);
        List<Map<String, Object>> documents = supplierService.listDocuments(id).stream()
                .map(document -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", document.getId());
                    item.put("supplierId", id);
                    item.put("documentType", document.getDocumentType());
                    item.put("originalFilename", document.getOriginalFilename());
                    item.put("filename", document.getOriginalFilename());
                    item.put("fileSizeBytes", document.getFileSizeBytes());
                    item.put("checksumSha256", document.getChecksumSha256());
                    item.put("uploadedAt", document.getUploadedAt());
                    return item;
                })
                .toList();
        return ApiResponse.success(documents);
    }

    @PostMapping("/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
    public ApiResponse<Map<String, Object>> uploadSupplierDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") SupplierDocumentType documentType,
            Authentication authentication) throws Exception {
        supplierService.getSupplier(id);
        User uploader = securityHelper.currentUser(authentication);
        byte[] bytes = file.getBytes();
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String objectKey = "supplier-docs/" + id + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        minioStorageService.upload(objectKey, bytes, file.getContentType());

        SupplierDocument document = supplierService.uploadDocument(
                id, documentType, file.getOriginalFilename(), objectKey,
                file.getSize(), checksum, uploader);

        return ApiResponse.success(Map.of(
                "id", document.getId(),
                "supplierId", id,
                "documentType", document.getDocumentType(),
                "originalFilename", document.getOriginalFilename(),
                "filename", document.getOriginalFilename()
        ));
    }
}
