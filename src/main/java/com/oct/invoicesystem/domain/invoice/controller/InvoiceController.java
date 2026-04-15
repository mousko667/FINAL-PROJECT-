package com.oct.invoicesystem.domain.invoice.controller;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceDTO;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceUpdateRequest;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'MANAGER', 'USER')")
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
        List<InvoiceDTO> mapped = paged.getContent().stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(
                new PagedResponse<>(mapped, paged.getPage(), paged.getSize(), paged.getTotalElements(), paged.getTotalPages(), paged.isLast())
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'MANAGER', 'USER')")
    @Operation(summary = "Get invoice by ID", description = "Retrieves a single invoice")
    public ResponseEntity<ApiResponse<InvoiceDTO>> getInvoiceById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(toDto(invoiceService.getById(id))));
    }

    @PostMapping
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Create draft invoice", description = "Creates a new invoice in BROUILLON status")
    public ResponseEntity<ApiResponse<InvoiceDTO>> createInvoice(
            @Valid @RequestBody InvoiceCreateRequest request,
            Authentication authentication) {
        UUID actorId = getActorId(authentication);
        Invoice created = invoiceService.createInvoice(toInvoice(request, actorId), actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toDto(created), "Invoice created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Update invoice", description = "Updates a BROUILLON or REJETE invoice")
    public ResponseEntity<ApiResponse<InvoiceDTO>> updateInvoice(
            @PathVariable UUID id,
            @Valid @RequestBody InvoiceUpdateRequest request,
            Authentication authentication) {
        UUID actorId = getActorId(authentication);
        Invoice updated = invoiceService.updateInvoice(id, toInvoice(request, actorId), actorId);
        return ResponseEntity.ok(ApiResponse.success(toDto(updated), "Invoice updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Soft delete invoice", description = "Soft-deletes a BROUILLON invoice")
    public ResponseEntity<ApiResponse<Void>> deleteInvoice(@PathVariable UUID id, Authentication authentication) {
        UUID actorId = getActorId(authentication);
        invoiceService.softDeleteInvoice(id, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Invoice deleted successfully"));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Submit invoice", description = "Submits a draft invoice for approval")
    public ResponseEntity<ApiResponse<Void>> submitInvoice(@PathVariable UUID id) {
        invoiceStateMachineService.sendEvent(id, InvoiceEvent.SUBMIT, null);
        return ResponseEntity.ok(ApiResponse.success(null, "action.submit.success"));
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Resubmit invoice", description = "Resubmits a rejected invoice for approval")
    public ResponseEntity<ApiResponse<Void>> resubmitInvoice(@PathVariable UUID id) {
        invoiceStateMachineService.sendEvent(id, InvoiceEvent.RESUBMIT, null);
        return ResponseEntity.ok(ApiResponse.success(null, "action.resubmit.success"));
    }

    private UUID getActorId(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
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
