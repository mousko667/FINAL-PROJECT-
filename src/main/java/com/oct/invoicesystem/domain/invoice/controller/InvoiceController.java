package com.oct.invoicesystem.domain.invoice.controller;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceDTO;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceUpdateRequest;
import com.oct.invoicesystem.domain.invoice.mapper.InvoiceMapper;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        PagedResponse<Invoice> paged = invoiceService.listInvoices(status, department, from, to, reference, null, page, size, sort);
        List<InvoiceDTO> mapped = paged.getContent().stream().map(invoiceMapper::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(
                new PagedResponse<>(mapped, paged.getPage(), paged.getSize(), paged.getTotalElements(), paged.getTotalPages(), paged.isLast())
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Get invoice by ID", description = "Retrieves a single invoice")
    public ResponseEntity<ApiResponse<InvoiceDTO>> getInvoiceById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoiceMapper.toDto(invoiceService.getById(id))));
    }

    @GetMapping("/pending-validation")
    @PreAuthorize("hasAnyRole('ADMIN', 'DAF', 'ASSISTANT_COMPTABLE') " +
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
    public ResponseEntity<ApiResponse<MatchingResultDTO>> getMatchingResult(@PathVariable UUID id) {
        ThreeWayMatchingResult result = invoiceService.getMatchingResult(id);
        return ResponseEntity.ok(ApiResponse.success(toMatchingDto(result)));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Get invoice status history", description = "Retrieves the full status transition history for an invoice")
    public ResponseEntity<ApiResponse<List<InvoiceHistoryDTO>>> getInvoiceHistory(@PathVariable UUID id) {
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

    @PostMapping("/{id}/matching/override")
    @PreAuthorize("hasAnyRole('DAF', 'ADMIN')")
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

    @GetMapping("/{id}/export/pdf")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")
    @Operation(summary = "Export invoice as PDF", description = "Generates a compliance-grade PDF for an invoice (AA, DAF, Admin, Validators)")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
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
