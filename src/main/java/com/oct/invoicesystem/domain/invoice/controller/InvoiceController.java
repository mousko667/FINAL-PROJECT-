package com.oct.invoicesystem.domain.invoice.controller;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceDTO;
import com.oct.invoicesystem.domain.invoice.dto.UpdateSensitivityRequest;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceUpdateRequest;
import com.oct.invoicesystem.domain.invoice.mapper.InvoiceMapper;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingResultDTO;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingOverrideRequest;
import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import com.oct.invoicesystem.domain.purchasing.service.ThreeWayMatchingService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.workflow.dto.InvoiceHistoryDTO;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oct.invoicesystem.domain.invoice.service.InvoicePdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoice Management", description = "Endpoints for managing supplier invoices")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceStateMachineService invoiceStateMachineService;
    private final ThreeWayMatchingService threeWayMatchingService;
    private final InvoicePdfService invoicePdfService;
    private final InvoiceMapper invoiceMapper;
    private final SecurityHelper securityHelper;
    private final com.oct.invoicesystem.shared.export.TabularExportService tabularExportService;
    private final com.oct.invoicesystem.domain.invoice.service.InvoiceImportService invoiceImportService;
    private final org.springframework.context.MessageSource messageSource;

    @GetMapping
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "List invoices", description = "Retrieves a paginated and filtered invoice list")
    public ResponseEntity<ApiResponse<PagedResponse<InvoiceDTO>>> listInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID department,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "issueDate,asc") String sort,
            Authentication authentication) {
        // N9: validators are confined to their own department; AA/DAF keep the global view.
        User currentUser = securityHelper.currentUser(authentication);
        PagedResponse<Invoice> paged = invoiceService.listInvoicesScoped(status, department, from, to, reference, null, page, size, sort, currentUser);
        List<InvoiceDTO> mapped = paged.getContent().stream().map(invoiceMapper::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(
                new PagedResponse<>(mapped, paged.getPage(), paged.getSize(), paged.getTotalElements(), paged.getTotalPages(), paged.isLast())
        ));
    }

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Export invoice list (csv|excel|pdf)",
            description = "Exports the filtered invoice list in the requested format")
    public ResponseEntity<byte[]> exportInvoices(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID department,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String reference,
            java.util.Locale locale,
            Authentication authentication) {
        var fmt = com.oct.invoicesystem.shared.export.TabularExportService.Format.from(format);
        // Build rows inside the service transaction so the lazy Department association can be read.
        // Headers + rows come from the single invoice-export source of truth (11 columns).
        // N9: a validator exports only their own department; AA/DAF export globally.
        User currentUser = securityHelper.currentUser(authentication);
        List<String> headers = invoiceService.invoiceExportHeaders(messageSource, locale);
        List<List<String>> rows = invoiceService.buildExportRowsScoped(status, department, from, to, reference, messageSource, locale, currentUser);
        String period = null;
        if (from != null || to != null) {
            String fromStr = from != null ? from.toString() : "...";
            String toStr = to != null ? to.toString() : "...";
            period = messageSource.getMessage("export.pdf.period", new Object[]{fromStr, toStr}, locale);
        }
        
        // Build filters label
        StringBuilder f = new StringBuilder();
        if (status != null)     f.append("Statut: ").append(status.name());
        if (department != null) f.append(f.length() > 0 ? " \u00B7 " : "").append("Departement: ").append(department);
        String filters = f.length() == 0 ? null : f.toString();
        String title = messageSource.getMessage("export.title.invoices", null, locale);
        com.oct.invoicesystem.shared.export.ReportMetadata meta =
                com.oct.invoicesystem.shared.export.ReportMetadata.of(securityHelper.currentUser(authentication), messageSource, period, filters, locale);
        byte[] body = tabularExportService.export(fmt, title, headers, rows, meta, messageSource);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoices_export." + fmt.extension)
                .contentType(MediaType.parseMediaType(fmt.mediaType))
                .body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Get invoice by ID", description = "Retrieves a single invoice")
    public ResponseEntity<ApiResponse<InvoiceDTO>> getInvoiceById(@PathVariable UUID id,
            Authentication authentication) {
        // N9: a validator may only read invoices of their own department (AA/DAF: global).
        User currentUser = securityHelper.currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(invoiceMapper.toDto(invoiceService.getByIdScoped(id, currentUser))));
    }

    @GetMapping("/duplicate-check")
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'SUPPLIER')")
    @Operation(summary = "Advisory duplicate pre-check",
            description = "Non-blocking check used while entering an invoice: returns whether a similar "
                    + "invoice (same supplier + description, last 365 days) already exists, so the UI can warn the user. "
                    + "Suppliers may only check their own supplier id; the query parameter is ignored for them.")
    public ResponseEntity<ApiResponse<com.oct.invoicesystem.domain.invoice.dto.DuplicateCheckDTO>> checkDuplicate(
            @RequestParam UUID supplierId,
            @RequestParam String description,
            Authentication authentication) {
        // Prevent a supplier from probing another supplier's invoices (IDOR): force their own id.
        UUID effectiveSupplierId = supplierId;
        User currentUser = securityHelper.currentUser(authentication);
        if (currentUser.getSupplier() != null) {
            effectiveSupplierId = currentUser.getSupplier().getId();
        }
        return ResponseEntity.ok(ApiResponse.success(invoiceService.checkDuplicate(effectiveSupplierId, description)));
    }

    @GetMapping("/pending-validation")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_DRH') or hasAuthority('ROLE_VALIDATEUR_N1_DG') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_INFO') or hasAuthority('ROLE_VALIDATEUR_N1_TERM') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_COM') or hasAuthority('ROLE_VALIDATEUR_N1_QHSSE') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_INFRA') or hasAuthority('ROLE_VALIDATEUR_N1_TECH') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N2_INFO') or hasAuthority('ROLE_VALIDATEUR_N2_INFRA') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N2_TECH')")
    @Operation(summary = "Pending validation queue", description = "Lists invoices waiting for N1/N2 validation")
    public ResponseEntity<ApiResponse<PagedResponse<InvoiceDTO>>> getPendingValidationQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,asc") String sort) {
        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1])
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
        Page<Invoice> pending = invoiceService.getPendingValidationQueue(pageable);
        List<InvoiceDTO> mapped = pending.getContent().stream().map(invoiceMapper::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(
                new PagedResponse<>(mapped, pending.getNumber(), pending.getSize(), pending.getTotalElements(), pending.getTotalPages(), pending.isLast())
        ));
    }

    @GetMapping("/{id}/matching")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Get invoice matching result", description = "Retrieves the latest three-way matching result for an invoice")
    public ResponseEntity<ApiResponse<MatchingResultDTO>> getMatchingResult(@PathVariable UUID id,
            Authentication authentication) {
        // N9: enforce departmental scope on the invoice before exposing its matching result.
        invoiceService.getByIdScoped(id, securityHelper.currentUser(authentication));
        ThreeWayMatchingResult result = invoiceService.getMatchingResult(id);
        return ResponseEntity.ok(ApiResponse.success(toMatchingDto(result)));
    }

    @GetMapping("/{id}/matching/export")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Export invoice matching reconciliation report (csv|excel|pdf)",
            description = "Exports the three-way matching reconciliation report for an invoice in the requested format")
    public ResponseEntity<byte[]> exportMatchingReport(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "csv") String format,
            java.util.Locale locale,
            Authentication authentication) {
        var fmt = com.oct.invoicesystem.shared.export.TabularExportService.Format.from(format);
        List<String> headers = List.of(
                messageSource.getMessage("report.excel.header.field", null, locale),
                messageSource.getMessage("report.excel.header.value", null, locale)
        );
        List<List<String>> rows = invoiceService.buildMatchingExportRows(id);
        String title = messageSource.getMessage("export.title.matching", null, locale);
        String periodLabel = null;
        // The endpoint currently does not accept from/to, so periodLabel remains null.
        com.oct.invoicesystem.shared.export.ReportMetadata meta =
                com.oct.invoicesystem.shared.export.ReportMetadata.of(securityHelper.currentUser(authentication), messageSource, periodLabel, null, locale);
        byte[] body = tabularExportService.export(fmt, title, headers, rows, meta, messageSource);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=matching_report_" + id + "." + fmt.extension)
                .contentType(MediaType.parseMediaType(fmt.mediaType))
                .body(body);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Get invoice status history", description = "Retrieves the full status transition history for an invoice")
    public ResponseEntity<ApiResponse<List<InvoiceHistoryDTO>>> getInvoiceHistory(@PathVariable UUID id,
            Authentication authentication) {
        // N9: enforce departmental scope before exposing an invoice's history.
        invoiceService.getByIdScoped(id, securityHelper.currentUser(authentication));
        List<InvoiceHistoryDTO> history = invoiceService.getInvoiceHistory(id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Create draft invoice", description = "Creates a new invoice in BROUILLON status")
    public ResponseEntity<ApiResponse<InvoiceDTO>> createInvoice(
            @Valid @RequestBody InvoiceCreateRequest request,
            Authentication authentication) {
        UUID actorId = securityHelper.currentUserId(authentication);
        Invoice created = invoiceService.createInvoice(toInvoice(request, actorId), actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(invoiceMapper.toDto(created), "Invoice created successfully"));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Import multiple draft invoices (CSV or XML)",
            description = "Bulk-creates draft invoices from a CSV (one row per invoice) or an XML document "
                    + "with several <invoice> elements. Best-effort with a per-line result. Distinct from "
                    + "the bulk document upload (several files for one invoice).")
    public ResponseEntity<ApiResponse<com.oct.invoicesystem.domain.invoice.dto.InvoiceImportResultDTO>> importInvoices(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "departmentCode", required = false) String departmentCode,
            Authentication authentication) {
        UUID actorId = securityHelper.currentUserId(authentication);
        var result = invoiceImportService.importInvoices(file, departmentCode, actorId);
        return ResponseEntity.ok(ApiResponse.success(result, "invoice.import.processed"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Update invoice", description = "Updates a BROUILLON or REJETE invoice")
    public ResponseEntity<ApiResponse<InvoiceDTO>> updateInvoice(
            @PathVariable UUID id,
            @Valid @RequestBody InvoiceUpdateRequest request,
            Authentication authentication) {
        UUID actorId = securityHelper.currentUserId(authentication);
        Invoice updated = invoiceService.updateInvoice(id, toInvoice(request, actorId), actorId);
        return ResponseEntity.ok(ApiResponse.success(invoiceMapper.toDto(updated), "Invoice updated successfully"));
    }

    @PatchMapping("/{id}/sensitivity")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Reclassify invoice sensitivity",
               description = "Sets the data-sensitivity level (PUBLIC/INTERNAL/CONFIDENTIAL) of an invoice")
    public ResponseEntity<ApiResponse<InvoiceDTO>> updateSensitivity(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSensitivityRequest request) {
        Invoice updated = invoiceService.updateDataSensitivity(id, request.dataSensitivity());
        return ResponseEntity.ok(ApiResponse.success(invoiceMapper.toDto(updated), "Sensitivity updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Soft delete invoice", description = "Soft-deletes a BROUILLON invoice")
    public ResponseEntity<ApiResponse<Void>> deleteInvoice(@PathVariable UUID id, Authentication authentication) {
        UUID actorId = securityHelper.currentUserId(authentication);
        invoiceService.softDeleteInvoice(id, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Invoice deleted successfully"));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Submit invoice", description = "Submits a draft invoice for approval")
    public ResponseEntity<ApiResponse<Void>> submitInvoice(@PathVariable UUID id, Authentication authentication) {
        UUID actorId = securityHelper.currentUserId(authentication);
        invoiceService.validateDocumentPresent(id);
        invoiceStateMachineService.sendEvent(id, InvoiceEvent.SUBMIT, java.util.Map.of(WorkflowExtendedStateKeys.USER_ID, actorId));
        return ResponseEntity.ok(ApiResponse.success(null, "action.submit.success"));
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Resubmit a rejected invoice",
               description = "Returns a REJETE invoice to SOUMIS for re-review after correction")
    public ResponseEntity<ApiResponse<Void>> resubmitInvoice(@PathVariable UUID id, Authentication authentication) {
        UUID actorId = securityHelper.currentUserId(authentication);
        invoiceStateMachineService.sendEvent(id, InvoiceEvent.RESUBMIT, java.util.Map.of(WorkflowExtendedStateKeys.USER_ID, actorId));
        return ResponseEntity.ok(ApiResponse.success(null, "action.resubmit.success"));
    }

    @PostMapping("/{id}/matching/override")
    @PreAuthorize("hasRole('DAF')")
    @Operation(summary = "Override three-way matching mismatch",
               description = "DAF or Admin can force an invoice through despite matching discrepancies")
    public ResponseEntity<ApiResponse<Void>> overrideMatchingMismatch(
            @PathVariable UUID id,
            @Valid @RequestBody MatchingOverrideRequest request,
            Authentication authentication) {
        User actor = securityHelper.currentUser(authentication);
        threeWayMatchingService.recordOverride(id, actor, request.overrideReason());
        return ResponseEntity.ok(ApiResponse.success(null, "action.mismatch_override.success"));
    }

    @GetMapping("/archive")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Rechercher dans les archives", description = "Full-text search dans les factures archivées")
    public ResponseEntity<ApiResponse<Page<InvoiceDTO>>> searchArchived(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String folderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InvoiceDTO> result = invoiceService.searchArchived(keyword, department, folderId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}/export/pdf")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Export invoice as PDF", description = "Generates a compliance-grade PDF for an invoice (AA, DAF, Admin, Validators)")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id, Authentication authentication) {
        // N9: a validator can only export the PDF of an invoice in their own department.
        invoiceService.getByIdScoped(id, securityHelper.currentUser(authentication));
        byte[] pdfBytes = invoicePdfService.generatePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "invoice-" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    private Invoice toInvoice(InvoiceCreateRequest request, UUID actorId) {
        Department department = new Department();
        department.setId(request.departmentId());
        User actor = new User();
        actor.setId(actorId);
        com.oct.invoicesystem.domain.supplier.model.Supplier supplier = null;
        if (request.supplierId() != null) {
            supplier = new com.oct.invoicesystem.domain.supplier.model.Supplier();
            supplier.setId(request.supplierId());
        }
        Invoice invoice = Invoice.builder()
                .department(department)
                .submittedBy(actor)
                .supplier(supplier)
                .purchaseOrderId(request.purchaseOrderId())
                .supplierName(request.supplierName())
                .supplierEmail(request.supplierEmail())
                .supplierTaxId(request.supplierTaxId())
                .supplierBankDetails(request.supplierBankDetails())
                .amount(request.amount())
                .currency(request.currency())
                .issueDate(request.issueDate())
                .dueDate(request.dueDate())
                .description(request.description())
                .build();
        attachLineItems(invoice, request.lineItems());
        return invoice;
    }

    /**
     * Attaches optional invoice lines to a freshly built invoice so the three-way matching engine
     * has line-level data to compare against the PO. Delegates to
     * {@link com.oct.invoicesystem.domain.invoice.service.InvoiceLineItemFactory} so the supplier
     * portal shares the exact same implementation (audit finding AUDIT-031).
     */
    private void attachLineItems(Invoice invoice, java.util.List<InvoiceCreateRequest.LineItem> lines) {
        com.oct.invoicesystem.domain.invoice.service.InvoiceLineItemFactory.attachLineItems(invoice, lines);
    }

    private Invoice toInvoice(InvoiceUpdateRequest request, UUID actorId) {
        Department department = new Department();
        department.setId(request.departmentId());
        User actor = new User();
        actor.setId(actorId);
        com.oct.invoicesystem.domain.supplier.model.Supplier supplier = null;
        if (request.supplierId() != null) {
            supplier = new com.oct.invoicesystem.domain.supplier.model.Supplier();
            supplier.setId(request.supplierId());
        }
        return Invoice.builder()
                .department(department)
                .submittedBy(actor)
                .supplier(supplier)
                .purchaseOrderId(request.purchaseOrderId())
                .supplierName(request.supplierName())
                .supplierEmail(request.supplierEmail())
                .supplierTaxId(request.supplierTaxId())
                .supplierBankDetails(request.supplierBankDetails())
                .amount(request.amount())
                .currency(request.currency())
                .issueDate(request.issueDate())
                .dueDate(request.dueDate())
                .description(request.description())
                .build();
    }

    private MatchingResultDTO toMatchingDto(ThreeWayMatchingResult result) {
        return new MatchingResultDTO(
                result.getId(),
                result.getInvoice() != null ? result.getInvoice().getId() : null,
                result.getPurchaseOrder() != null ? result.getPurchaseOrder().getId() : null,
                result.getGoodsReceiptNote() != null ? result.getGoodsReceiptNote().getId() : null,
                result.getStatus(),
                result.getDiscrepancyNotes(),
                result.getOverriddenBy() != null ? result.getOverriddenBy().getId() : null,
                result.getOverrideReason(),
                result.getCreatedAt()
        );
    }
}
