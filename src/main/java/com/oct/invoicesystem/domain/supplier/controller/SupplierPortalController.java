package com.oct.invoicesystem.domain.supplier.controller;

import com.oct.invoicesystem.domain.invoice.dto.InvoiceCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceDTO;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.supplier.dto.SupplierUpdateRequest;
import com.oct.invoicesystem.domain.supplier.dto.SupplierResponse;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.service.SupplierService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import com.oct.invoicesystem.domain.department.model.Department;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;

import com.oct.invoicesystem.domain.supplier.model.SupplierDocumentType;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocument;
import com.oct.invoicesystem.domain.supplier.repository.SupplierDocumentRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/supplier")
@RequiredArgsConstructor
@Tag(name = "Supplier Portal", description = "Endpoints exclusively for ROLE_SUPPLIER to manage their data")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPPLIER')")
public class SupplierPortalController {

    private final InvoiceService invoiceService;
    private final SupplierService supplierService;
    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierDocumentRepository supplierDocumentRepository;
    private final MinioStorageService minioStorageService;
    private final org.springframework.context.MessageSource messageSource;

    private UUID getSupplierId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getSupplier() == null) {
            throw new ValidationException("Supplier not linked to user");
        }
        return user.getSupplier().getId();
    }

    private User getUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @PostMapping("/invoices")
    @Operation(summary = "Submit new invoice", description = "Allows supplier to submit an invoice")
    public ResponseEntity<ApiResponse<InvoiceDTO>> submitInvoice(
            @Valid @RequestBody InvoiceCreateRequest request,
            Authentication authentication,
            java.util.Locale locale) {
        User user = getUser(authentication);
        UUID supplierId = getSupplierId(authentication);

        // Security override: Force supplierId
        Invoice invoicePayload = toInvoice(request, user.getId(), supplierId);
        
        // Use invoiceService or directly save? Wait, invoiceService ensures ASSISTANT_COMPTABLE!
        // We must implement a supplierCreateInvoice method in InvoiceService or do it here.
        // Actually, we can bypass createInvoice and just save, or better: modify invoiceService to allow Supplier?
        // Let's create it directly here to ensure separation of concerns.
        Invoice created = invoiceService.createSupplierInvoice(invoicePayload, user.getId(), supplierId);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toDto(created), messageSource.getMessage("supplier.invoice.submitted", null, locale)));
    }

    @GetMapping("/invoices")
    @Operation(summary = "List supplier invoices", description = "Retrieves paginated list of ONLY this supplier's invoices")
    public ResponseEntity<ApiResponse<PagedResponse<InvoiceDTO>>> listMyInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Authentication authentication) {
        
        UUID supplierId = getSupplierId(authentication);
        PagedResponse<Invoice> paged = invoiceService.listInvoices(status, null, from, to, reference, supplierId, page, size, sort);
        List<InvoiceDTO> mapped = paged.getContent().stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(
                new PagedResponse<>(mapped, paged.getPage(), paged.getSize(), paged.getTotalElements(), paged.getTotalPages(), paged.isLast())
        ));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get supplier profile", description = "Returns the supplier's own profile")
    public ResponseEntity<ApiResponse<Supplier>> getProfile(Authentication authentication) {
        UUID supplierId = getSupplierId(authentication);
        Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        return ResponseEntity.ok(ApiResponse.success(supplier));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update supplier profile", description = "Updates the supplier's own profile")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateProfile(
            @Valid @RequestBody SupplierUpdateRequest request,
            Authentication authentication,
            java.util.Locale locale) {
        UUID supplierId = getSupplierId(authentication);
        SupplierResponse response = supplierService.updateSupplier(supplierId, request);
        return ResponseEntity.ok(ApiResponse.success(response, messageSource.getMessage("supplier.profile.updated", null, locale)));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard statistics", description = "Returns invoice status counts for the supplier")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(Authentication authentication) {
        UUID supplierId = getSupplierId(authentication);
        
        Map<String, Object> stats = new HashMap<>();
        Map<String, Long> statusCounts = invoiceService.getSupplierInvoiceStatusCounts(supplierId);
        stats.put("statusCounts", statusCounts);
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PostMapping(value = "/documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload supplier document", description = "Uploads a tax certificate or contract for the supplier")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") SupplierDocumentType documentType,
            Authentication authentication,
            java.util.Locale locale) {
        User user = getUser(authentication);
        UUID supplierId = getSupplierId(authentication);

        try {
            byte[] bytes = file.getBytes();
            String checksum = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));

            String objectKey = "supplier-docs/" + supplierId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
            minioStorageService.upload(objectKey, bytes, file.getContentType());

            Supplier supplier = new Supplier();
            supplier.setId(supplierId);

            SupplierDocument doc = SupplierDocument.builder()
                    .supplier(supplier)
                    .documentType(documentType)
                    .originalFilename(file.getOriginalFilename())
                    .minioObjectKey(objectKey)
                    .fileSizeBytes(file.getSize())
                    .checksumSha256(checksum)
                    .uploadedBy(user)
                    .build();
            supplierDocumentRepository.save(doc);

            Map<String, Object> result = new HashMap<>();
            result.put("id", doc.getId());
            result.put("filename", doc.getOriginalFilename());
            result.put("documentType", documentType);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(result, messageSource.getMessage("supplier.document.uploaded", null, locale)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    @GetMapping("/documents")
    @Operation(summary = "List supplier documents", description = "Lists all documents uploaded by this supplier")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listDocuments(Authentication authentication) {
        UUID supplierId = getSupplierId(authentication);
        List<SupplierDocument> docs = supplierDocumentRepository.findBySupplierId(supplierId);
        List<Map<String, Object>> result = docs.stream().map(d -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId());
            m.put("filename", d.getOriginalFilename());
            m.put("documentType", d.getDocumentType());
            m.put("uploadedAt", d.getUploadedAt());
            m.put("fileSizeBytes", d.getFileSizeBytes());
            return m;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private Invoice toInvoice(InvoiceCreateRequest request, UUID actorId, UUID supplierId) {
        Department department = new Department();
        department.setId(request.departmentId());
        User actor = new User();
        actor.setId(actorId);

        Supplier supplier = new Supplier();
        supplier.setId(supplierId);

        return Invoice.builder()
                .department(department)
                .submittedBy(actor)
                .supplier(supplier)
                .amount(request.amount())
                .currency(request.currency())
                .issueDate(request.issueDate())
                .dueDate(request.dueDate())
                .description(request.description())
                .status(InvoiceStatus.BROUILLON)
                .build();
    }

    private InvoiceDTO toDto(Invoice invoice) {
        return new InvoiceDTO(
                invoice.getId(),
                invoice.getReferenceNumber(),
                invoice.getDepartment() != null ? invoice.getDepartment().getId() : null,
                invoice.getSubmittedBy() != null ? invoice.getSubmittedBy().getId() : null,
                invoice.getSupplier() != null ? invoice.getSupplier().getId() : null,
                invoice.getSupplierName(),
                invoice.getSupplierEmail(),
                invoice.getSupplierTaxId(),
                invoice.getAmount(),
                invoice.getCurrency(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getDescription(),
                invoice.getStatus(),
                invoice.getVersion(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
