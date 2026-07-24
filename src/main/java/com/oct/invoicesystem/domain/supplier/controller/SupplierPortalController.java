package com.oct.invoicesystem.domain.supplier.controller;

import com.oct.invoicesystem.domain.invoice.dto.InvoiceCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceDocumentDTO;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceDTO;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.service.InvoiceDocumentService;
import com.oct.invoicesystem.domain.invoice.service.InvoiceLineItemFactory;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.mapper.InvoiceMapper;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderItemDTO;
import com.oct.invoicesystem.domain.purchasing.service.PurchaseOrderService;
import com.oct.invoicesystem.domain.supplier.dto.SupplierPurchaseOrderDTO;
import com.oct.invoicesystem.domain.supplier.dto.SupplierUpdateRequest;
import com.oct.invoicesystem.domain.supplier.dto.SupplierResponse;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocument;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocumentType;
import com.oct.invoicesystem.domain.supplier.service.SupplierService;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.shared.util.SecurityHelper;
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
    private final InvoiceStateMachineService invoiceStateMachineService;
    private final InvoiceDocumentService invoiceDocumentService;
    private final SupplierService supplierService;
    private final PurchaseOrderService purchaseOrderService;
    private final MinioStorageService minioStorageService;
    private final SecurityHelper securityHelper;
    private final org.springframework.context.MessageSource messageSource;
    private final InvoiceMapper invoiceMapper;

    private UUID getSupplierId(Authentication authentication) {
        User user = securityHelper.currentUser(authentication);
        if (user.getSupplier() == null) {
            throw new ValidationException("Supplier not linked to user");
        }
        return user.getSupplier().getId();
    }

    @PostMapping("/invoices")
    @Operation(summary = "Submit new invoice", description = "Allows supplier to submit an invoice")
    public ResponseEntity<ApiResponse<InvoiceDTO>> submitInvoice(
            @Valid @RequestBody InvoiceCreateRequest request,
            Authentication authentication,
            java.util.Locale locale) {
        User user = securityHelper.currentUser(authentication);
        UUID supplierId = getSupplierId(authentication);

        Invoice invoicePayload = toInvoice(request, user.getId(), supplierId);
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
        List<InvoiceDTO> mapped = paged.getContent().stream().map(invoiceMapper::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(
                new PagedResponse<>(mapped, paged.getPage(), paged.getSize(), paged.getTotalElements(), paged.getTotalPages(), paged.isLast())
        ));
    }

    @PostMapping("/invoices/{invoiceId}/submit")
    @Operation(summary = "Submit supplier invoice", description = "Moves the supplier's own draft invoice into the validation workflow")
    public ResponseEntity<ApiResponse<Void>> submitMyInvoice(
            @PathVariable UUID invoiceId,
            Authentication authentication,
            java.util.Locale locale) {
        User user = securityHelper.currentUser(authentication);
        ensureOwnInvoice(invoiceId, getSupplierId(authentication));
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.SUBMIT,
                Map.of(WorkflowExtendedStateKeys.USER_ID, user.getId()));
        return ResponseEntity.ok(ApiResponse.success(null, messageSource.getMessage("action.submit.success", null, locale)));
    }

    @PostMapping("/invoices/{invoiceId}/resubmit")
    @Operation(summary = "Resubmit supplier invoice", description = "Resubmits the supplier's own rejected invoice")
    public ResponseEntity<ApiResponse<Void>> resubmitMyInvoice(
            @PathVariable UUID invoiceId,
            Authentication authentication,
            java.util.Locale locale) {
        User user = securityHelper.currentUser(authentication);
        ensureOwnInvoice(invoiceId, getSupplierId(authentication));
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.RESUBMIT,
                Map.of(WorkflowExtendedStateKeys.USER_ID, user.getId()));
        return ResponseEntity.ok(ApiResponse.success(null, messageSource.getMessage("action.resubmit.success", null, locale)));
    }

    @PostMapping(value = "/invoices/{invoiceId}/documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload supplier invoice document", description = "Uploads a supporting invoice document for the supplier's own invoice")
    public ResponseEntity<ApiResponse<InvoiceDocumentDTO>> uploadMyInvoiceDocument(
            @PathVariable UUID invoiceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            java.util.Locale locale) throws Exception {
        User user = securityHelper.currentUser(authentication);
        ensureOwnInvoice(invoiceId, getSupplierId(authentication));
        InvoiceDocument document = invoiceDocumentService.upload(invoiceId, file, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toDocumentDto(document), messageSource.getMessage("supplier.document.uploaded", null, locale)));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get supplier profile", description = "Returns the supplier's own profile")
    public ResponseEntity<ApiResponse<SupplierResponse>> getProfile(Authentication authentication) {
        UUID supplierId = getSupplierId(authentication);
        SupplierResponse response = supplierService.getSupplier(supplierId);
        return ResponseEntity.ok(ApiResponse.success(response));
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
    @Operation(summary = "Dashboard statistics", description = "Returns invoice status counts, matching status breakdown, and next expected payment date for the supplier")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(Authentication authentication) {
        UUID supplierId = getSupplierId(authentication);

        Map<String, Object> stats = new HashMap<>();
        Map<String, Long> statusCounts = invoiceService.getSupplierInvoiceStatusCounts(supplierId);
        stats.put("statusCounts", statusCounts);
        stats.put("submittedCount", statusCounts.getOrDefault(InvoiceStatus.SOUMIS.name(), 0L));
        stats.put("pendingCount",
                statusCounts.getOrDefault(InvoiceStatus.SOUMIS.name(), 0L)
                        + statusCounts.getOrDefault(InvoiceStatus.EN_CONTROLE_AA.name(), 0L)
                        + statusCounts.getOrDefault(InvoiceStatus.EN_VALIDATION_N1.name(), 0L)
                        + statusCounts.getOrDefault(InvoiceStatus.EN_VALIDATION_N2.name(), 0L));
        stats.put("approvedCount",
                statusCounts.getOrDefault(InvoiceStatus.VALIDE.name(), 0L)
                        + statusCounts.getOrDefault(InvoiceStatus.BON_A_PAYER.name(), 0L));
        stats.put("paidCount",
                statusCounts.getOrDefault(InvoiceStatus.PAYE.name(), 0L)
                        + statusCounts.getOrDefault(InvoiceStatus.ARCHIVE.name(), 0L));
        stats.put("rejectedCount", statusCounts.getOrDefault(InvoiceStatus.REJETE.name(), 0L));

        Map<String, Long> matchingStatusCounts = invoiceService.getSupplierInvoiceMatchingStatusCounts(supplierId);
        stats.put("matchingStatusBreakdown", matchingStatusCounts);

        java.time.LocalDate nextExpectedPaymentDate = invoiceService.getSupplierNextExpectedPaymentDate(supplierId);
        stats.put("nextExpectedPaymentDate", nextExpectedPaymentDate);

        PagedResponse<Invoice> pendingInvoices = invoiceService.listInvoices(null, null, null, null, null, supplierId, 0, 5, "dueDate,asc");
        stats.put("pendingActions", pendingInvoices.getContent().stream()
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.REJETE || invoice.getStatus() == InvoiceStatus.BROUILLON)
                .map(invoice -> Map.of(
                        "id", invoice.getId(),
                        "referenceNumber", invoice.getReferenceNumber(),
                        "status", invoice.getStatus().name(),
                        "dueDate", invoice.getDueDate()
                ))
                .toList());

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PostMapping(value = "/documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload supplier document", description = "Uploads a tax certificate or contract for the supplier")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") SupplierDocumentType documentType,
            Authentication authentication,
            java.util.Locale locale) throws Exception {
        // AUDIT-006: no raw try/catch here (CLAUDE.md §3). The former catch(Exception) rethrew as
        // RuntimeException, which would requalify a business ValidationException (empty/oversized/
        // wrong-MIME file) into a generic 500. Let checked exceptions propagate (throws Exception,
        // like InvoiceDocumentController) so GlobalExceptionHandler maps each to its proper status —
        // the same handler already turns oversized uploads into 413 and bad MIME into 400.
        User user = securityHelper.currentUser(authentication);
        UUID supplierId = getSupplierId(authentication);

        byte[] bytes = file.getBytes();
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));

        String objectKey = "supplier-docs/" + supplierId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        minioStorageService.upload(objectKey, bytes, file.getContentType());

        SupplierDocument doc = supplierService.uploadDocument(
                supplierId, documentType, file.getOriginalFilename(),
                objectKey, file.getSize(), checksum, user);

        Map<String, Object> result = new HashMap<>();
        result.put("id", doc.getId());
        result.put("filename", doc.getOriginalFilename());
        result.put("documentType", documentType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, messageSource.getMessage("supplier.document.uploaded", null, locale)));
    }

    /**
     * Lists the purchase orders this supplier may invoice against, so the portal can offer a PO
     * selector instead of leaving the field unreachable (audit finding AUDIT-001).
     *
     * <p>The scope comes from the authenticated supplier's own id, never from a request parameter,
     * and only {@code OPEN} orders are returned. The response deliberately omits internal OCT data
     * (creator, supplier id) — see {@link SupplierPurchaseOrderDTO}.</p>
     */
    @GetMapping("/purchase-orders")
    @Operation(summary = "List own open purchase orders",
            description = "Purchase orders this supplier can invoice against, used by the PO selector")
    public ResponseEntity<ApiResponse<List<SupplierPurchaseOrderDTO>>> listMyPurchaseOrders(
            Authentication authentication) {
        UUID supplierId = getSupplierId(authentication);
        List<SupplierPurchaseOrderDTO> result = purchaseOrderService.listOpenBySupplier(supplierId).stream()
                .map(po -> new SupplierPurchaseOrderDTO(
                        po.getId(),
                        po.getPoNumber(),
                        po.getTotalAmount(),
                        po.getItems().stream()
                                .map(i -> new PurchaseOrderItemDTO(
                                        i.getId(),
                                        i.getItemDescription(),
                                        i.getQuantity(),
                                        i.getUnitPrice(),
                                        i.getLineTotal()))
                                .toList()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/documents")
    @Operation(summary = "List supplier documents", description = "Lists all documents uploaded by this supplier")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listDocuments(Authentication authentication) {
        UUID supplierId = getSupplierId(authentication);
        List<SupplierDocument> docs = supplierService.listDocuments(supplierId);
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
        // AUDIT-002: the portal used to copy request.purchaseOrderId() verbatim, so a supplier could
        // create an invoice against ANOTHER supplier's purchase order (proven in runtime: 201
        // Created). Refuse at the source, with a message that reveals nothing about that order —
        // not even whether it exists. Submission-time matching enforces the same rule again.
        // The allowed set is exactly what the PO selector offers (listOpenBySupplier), so the API
        // and the form can never disagree on what this supplier may invoice against.
        if (request.purchaseOrderId() != null
                && purchaseOrderService.listOpenBySupplier(supplierId).stream()
                        .noneMatch(po -> request.purchaseOrderId().equals(po.getId()))) {
            throw new ValidationException("matching.po.not_owned");
        }
        Department department = new Department();
        department.setId(request.departmentId());
        User actor = new User();
        actor.setId(actorId);

        Supplier supplier = new Supplier();
        supplier.setId(supplierId);

        Invoice invoice = Invoice.builder()
                .department(department)
                .submittedBy(actor)
                .supplier(supplier)
                .purchaseOrderId(request.purchaseOrderId())
                .amount(request.amount())
                .currency(request.currency())
                .issueDate(request.issueDate())
                .dueDate(request.dueDate())
                .description(request.description())
                .status(InvoiceStatus.BROUILLON)
                .build();
        // AUDIT-031: the portal used to drop request.lineItems() silently, so any portal invoice
        // referencing a PO was rejected at submission ("Invoice or PO has no line items").
        // Shares the internal path's implementation so the two cannot drift apart again.
        InvoiceLineItemFactory.attachLineItems(invoice, request.lineItems());
        return invoice;
    }

    private InvoiceDTO toDto(Invoice invoice) {
        return invoiceMapper.toDto(invoice);
    }

    private void ensureOwnInvoice(UUID invoiceId, UUID supplierId) {
        Invoice invoice = invoiceService.getById(invoiceId);
        if (invoice.getSupplier() == null || !supplierId.equals(invoice.getSupplier().getId())) {
            throw new ValidationException("Supplier can only access own invoice");
        }
    }

    private InvoiceDocumentDTO toDocumentDto(InvoiceDocument document) {
        return new InvoiceDocumentDTO(
                document.getId(),
                document.getInvoice() != null ? document.getInvoice().getId() : null,
                document.getOriginalFilename(),
                document.getFileType(),
                document.getFileSizeBytes(),
                document.getChecksumSha256(),
                document.getUploadedBy() != null ? document.getUploadedBy().getId() : null,
                document.getUploadedAt(),
                document.getVersion(),
                document.getSupersededByDocumentId()
        );
    }
}
