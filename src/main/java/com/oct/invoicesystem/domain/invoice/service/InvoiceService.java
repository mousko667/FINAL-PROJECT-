package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceDTO;
import com.oct.invoicesystem.domain.invoice.mapper.InvoiceMapper;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.dto.InvoiceHistoryDTO;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import com.oct.invoicesystem.shared.response.PagedResponse;
import com.oct.invoicesystem.shared.util.ReferenceNumberGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final String ROLE_ASSISTANT_COMPTABLE = "ROLE_ASSISTANT_COMPTABLE";

    private final InvoiceRepository invoiceRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final ReferenceNumberGenerator referenceNumberGenerator;
    private final InvoiceDocumentRepository invoiceDocumentRepository;
    private final ThreeWayMatchingResultRepository matchingResultRepository;
    private final InvoiceStatusHistoryRepository historyRepository;
    private final InvoiceMapper invoiceMapper;

    /**
     * Creates an invoice draft in BROUILLON status.
     *
     * @param invoice the invoice payload to persist
     * @param actorId the authenticated actor creating the draft
     * @return persisted draft invoice
     */
    @Transactional
    public Invoice createInvoice(Invoice invoice, UUID actorId) {
        ensureAssistantComptable(actorId);

        invoice.setDepartment(resolveDepartment(invoice.getDepartment()));
        populateSupplierFields(invoice);

        invoice.setId(null);
        invoice.setReferenceNumber(referenceNumberGenerator.nextReferenceNumber());
        invoice.setStatus(InvoiceStatus.BROUILLON);
        invoice.setDeletedAt(null);
        return invoiceRepository.save(invoice);
    }

    /**
     * Updates an existing invoice when editable.
     *
     * @param invoiceId target invoice id
     * @param updatedInvoice new data
     * @param actorId actor requesting update
     * @return updated invoice
     */
    @Transactional
    public Invoice updateInvoice(UUID invoiceId, Invoice updatedInvoice, UUID actorId) {
        ensureAssistantComptable(actorId);
        Invoice existing = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));

        if (existing.getStatus() != InvoiceStatus.BROUILLON && existing.getStatus() != InvoiceStatus.REJETE) {
            throw new WorkflowException("Only BROUILLON or REJETE invoices can be updated");
        }

        existing.setSupplier(updatedInvoice.getSupplier());
        existing.setSupplierName(updatedInvoice.getSupplierName());
        existing.setSupplierEmail(updatedInvoice.getSupplierEmail());
        existing.setSupplierTaxId(updatedInvoice.getSupplierTaxId());
        existing.setSupplierBankDetails(updatedInvoice.getSupplierBankDetails());
        existing.setPurchaseOrderId(updatedInvoice.getPurchaseOrderId());

        populateSupplierFields(existing);

        existing.setDepartment(resolveDepartment(updatedInvoice.getDepartment()));
        existing.setAmount(updatedInvoice.getAmount());
        existing.setCurrency(updatedInvoice.getCurrency());
        existing.setIssueDate(updatedInvoice.getIssueDate());
        existing.setDueDate(updatedInvoice.getDueDate());
        existing.setDescription(updatedInvoice.getDescription());

        return invoiceRepository.save(existing);
    }

    /**
     * Updates the data-sensitivity classification of an invoice (P11-15). This is metadata, not
     * invoice content, so it can be reclassified at any workflow status. Role enforcement (DAF or
     * Assistant Comptable) is applied by the controller's {@code @PreAuthorize}.
     *
     * @param invoiceId target invoice id
     * @param sensitivity the new sensitivity level
     * @return the updated invoice
     */
    @Transactional
    public Invoice updateDataSensitivity(UUID invoiceId,
            com.oct.invoicesystem.domain.invoice.model.DataSensitivity sensitivity) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
        invoice.setDataSensitivity(sensitivity);
        return invoiceRepository.save(invoice);
    }

    /**
     * Soft deletes an invoice by setting deletedAt.
     *
     * @param invoiceId target invoice id
     * @param actorId actor requesting deletion
     */
    @Transactional
    public void softDeleteInvoice(UUID invoiceId, UUID actorId) {
        ensureAssistantComptable(actorId);
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
        if (invoice.getStatus() != InvoiceStatus.BROUILLON) {
            throw new WorkflowException("Only BROUILLON invoices can be deleted");
        }
        invoice.setDeletedAt(Instant.now());
        invoiceRepository.save(invoice);
    }

    /**
     * Fetches one invoice by id if not soft-deleted.
     *
     * @param invoiceId invoice identifier
     * @return invoice
     */
    @Transactional(readOnly = true)
    public Invoice getById(UUID invoiceId) {
        return invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
    }

    /**
     * Lists invoices with optional filters and pagination.
     *
     * @param status optional status
     * @param sort sort pattern field,direction
     * @return paged invoice data
     */
    @Transactional(readOnly = true)
    public PagedResponse<Invoice> listInvoices(
            InvoiceStatus status,
            UUID departmentId,
            LocalDate fromDate,
            LocalDate toDate,
            String reference,
            UUID supplierId,
            int page,
            int size,
            String sort
    ) {
        String[] sortParams = sort.split(",");
        String sortBy = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1])
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<Invoice> invoices = invoiceRepository.findAllWithFilters(
                status,
                departmentId,
                fromDate,
                toDate,
                reference,
                supplierId,
                pageable
        );
        List<Invoice> data = invoices.getContent();
        return new PagedResponse<>(
                data,
                invoices.getNumber(),
                invoices.getSize(),
                invoices.getTotalElements(),
                invoices.getTotalPages(),
                invoices.isLast()
        );
    }

    /**
     * The 11 invoice-export column headers, localized, in the exact order produced by
     * {@link #buildExportRows}. Single source of truth so every export path (direct CSV/Excel/PDF,
     * the {@code /reports/export/excel} Excel, and the M11 INVOICES dataset) shares one header list.
     */
    public List<String> invoiceExportHeaders(org.springframework.context.MessageSource messageSource,
                                             java.util.Locale locale) {
        return List.of(
                messageSource.getMessage("report.excel.header.reference", null, locale),
                messageSource.getMessage("report.excel.header.supplier", null, locale),
                messageSource.getMessage("report.excel.header.supplier_email", null, locale),
                messageSource.getMessage("report.excel.header.amount", null, locale),
                messageSource.getMessage("report.excel.header.currency", null, locale),
                messageSource.getMessage("report.excel.header.status", null, locale),
                messageSource.getMessage("report.excel.header.department", null, locale),
                messageSource.getMessage("report.excel.header.issue_date", null, locale),
                messageSource.getMessage("report.excel.header.due_date", null, locale),
                messageSource.getMessage("report.excel.header.created_at", null, locale),
                messageSource.getMessage("report.excel.header.matching_status", null, locale));
    }

    /**
     * Single source of truth for the invoice-export column layout, shared by the direct Excel export
     * ({@code /reports/export/excel}) and the M11 report builder's INVOICES dataset so the two never
     * diverge. Columns (11): reference, supplier, supplier email, amount, currency, translated status,
     * department code, issue date, due date, created-at, matching status. Runs read-only so the lazy
     * {@code department} association can be read while building each row.
     *
     * <p>The matching status is resolved in a single batch query (no N+1) and rendered as the raw
     * {@code MatchingStatus} name ({@code MATCHED}/{@code MISMATCH}/…), or "-" when the invoice has no
     * matching result. The workflow status is localized via {@code invoice.status.<name>} so the export
     * shows human-readable labels, never the raw enum.</p>
     */
    @Transactional(readOnly = true)
    public List<java.util.List<String>> buildExportRows(
            InvoiceStatus status, UUID departmentId, LocalDate fromDate, LocalDate toDate, String reference,
            org.springframework.context.MessageSource messageSource, java.util.Locale locale) {
        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                status, departmentId, fromDate, toDate, reference, null,
                PageRequest.of(0, 10000, Sort.by(Sort.Direction.ASC, "issueDate"))).getContent();

        // Batch-resolve matching status for all invoices in one query (avoids N+1).
        java.util.Map<UUID, String> matchingByInvoice = new java.util.HashMap<>();
        List<UUID> ids = invoices.stream().map(Invoice::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : matchingResultRepository.findLatestStatusByInvoiceIds(ids)) {
                matchingByInvoice.put((UUID) row[0], String.valueOf(row[1]));
            }
        }

        return invoices.stream().map(i -> java.util.List.of(
                i.getReferenceNumber() == null ? "" : i.getReferenceNumber(),
                i.getSupplierName() == null ? "" : i.getSupplierName(),
                i.getSupplierEmail() == null ? "" : i.getSupplierEmail(),
                i.getAmount() == null ? "" : i.getAmount().toPlainString(),
                i.getCurrency() == null ? "" : i.getCurrency(),
                i.getStatus() == null ? "" : messageSource.getMessage(
                        "invoice.status." + i.getStatus().name().toLowerCase(), null, locale),
                i.getDepartment() == null ? "" : i.getDepartment().getCode(),
                i.getIssueDate() == null ? "" : i.getIssueDate().toString(),
                i.getDueDate() == null ? "" : i.getDueDate().toString(),
                i.getCreatedAt() == null ? "" : i.getCreatedAt().toString(),
                matchingByInvoice.getOrDefault(i.getId(), "-"))).toList();
    }

    @Transactional(readOnly = true)
    public Page<Invoice> getPendingValidationQueue(Pageable pageable) {
        return invoiceRepository.findPendingValidationQueue(pageable);
    }

    @Transactional(readOnly = true)
    public ThreeWayMatchingResult getMatchingResult(UUID invoiceId) {
        return matchingResultRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Matching result not found for invoice: " + invoiceId));
    }

    /**
     * Builds the rows of the three-way matching reconciliation report for a single invoice as a
     * Field/Value table (B2). Runs inside a read-only transaction so the lazy associations of
     * {@link ThreeWayMatchingResult} (invoice, purchase order, GRN, overriddenBy) can be read while
     * the report is assembled. Throws {@link ResourceNotFoundException} when no matching result
     * exists for the invoice (same contract as {@link #getMatchingResult}).
     */
    @Transactional(readOnly = true)
    public List<java.util.List<String>> buildMatchingExportRows(UUID invoiceId) {
        ThreeWayMatchingResult r = getMatchingResult(invoiceId);
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        rows.add(java.util.List.of("Invoice reference",
                r.getInvoice() != null && r.getInvoice().getReferenceNumber() != null ? r.getInvoice().getReferenceNumber() : ""));
        rows.add(java.util.List.of("Purchase order",
                r.getPurchaseOrder() != null && r.getPurchaseOrder().getPoNumber() != null ? r.getPurchaseOrder().getPoNumber() : ""));
        rows.add(java.util.List.of("Goods receipt note",
                r.getGoodsReceiptNote() != null && r.getGoodsReceiptNote().getGrnNumber() != null ? r.getGoodsReceiptNote().getGrnNumber() : ""));
        rows.add(java.util.List.of("Matching status", r.getStatus() == null ? "" : r.getStatus().name()));
        rows.add(java.util.List.of("Discrepancy notes", r.getDiscrepancyNotes() == null ? "" : r.getDiscrepancyNotes()));
        rows.add(java.util.List.of("Overridden by",
                r.getOverriddenBy() != null && r.getOverriddenBy().getUsername() != null ? r.getOverriddenBy().getUsername() : ""));
        rows.add(java.util.List.of("Override reason", r.getOverrideReason() == null ? "" : r.getOverrideReason()));
        rows.add(java.util.List.of("Generated at", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString()));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<InvoiceHistoryDTO> getInvoiceHistory(UUID invoiceId) {
        getById(invoiceId); // validates existence
        return historyRepository.findHistoryDTOsByInvoiceId(invoiceId);
    }

    @Transactional(readOnly = true)
    public void validateDocumentPresent(UUID invoiceId) {
        if (invoiceDocumentRepository.findByInvoiceId(invoiceId).isEmpty()) {
            throw new ValidationException("error.invoice.document_required");
        }
    }

    private Department resolveDepartment(Department department) {
        if (department == null || department.getId() == null) {
            throw new ValidationException("Department is required");
        }
        return departmentRepository.findById(department.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + department.getId()));
    }

    private void ensureAssistantComptable(UUID actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + actorId));
        boolean allowed = actor.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ASSISTANT_COMPTABLE::equals);
        if (!allowed) {
            throw new ValidationException("Only ASSISTANT_COMPTABLE can create and submit invoices");
        }
    }

    private void populateSupplierFields(Invoice invoice) {
        if (invoice.getSupplier() != null && invoice.getSupplier().getId() != null) {
            Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(invoice.getSupplier().getId())
                    .orElseThrow(() -> new ValidationException("Supplier not found or deleted"));
            invoice.setSupplierName(supplier.getCompanyName());
            invoice.setSupplierEmail(supplier.getContactEmail());
            invoice.setSupplierTaxId(supplier.getTaxId());
            invoice.setSupplierBankDetails(supplier.getBankDetails());
        } else {
            if (invoice.getSupplierName() == null || invoice.getSupplierName().trim().isEmpty()) {
                throw new ValidationException("Supplier name is required when no supplier is linked");
            }
            if (invoice.getSupplierEmail() == null || invoice.getSupplierEmail().trim().isEmpty()) {
                throw new ValidationException("Supplier email is required when no supplier is linked");
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<InvoiceDTO> searchArchived(String keyword, String departmentCode, String folderId, Instant from, Instant to, Pageable pageable) {
        UUID departmentId = null;
        if (departmentCode != null && !departmentCode.trim().isEmpty()) {
            departmentId = departmentRepository.findByCode(departmentCode)
                .map(Department::getId)
                .orElse(UUID.randomUUID()); // If not found, use a random UUID so it matches nothing
        }
        // Map to DTO inside the transaction: searchArchived is a native query whose lazy
        // Department proxy would otherwise fail with LazyInitializationException once the
        // session closes (the controller used to map after the tx boundary).
        return invoiceRepository.searchArchived(keyword, departmentId, folderId, from, to, pageable)
                .map(invoiceMapper::toDto);
    }

    @Transactional
    public Invoice createSupplierInvoice(Invoice invoice, UUID actorId, UUID supplierId) {
        // Enforce supplier ID override for security
        Supplier supplier = new Supplier();
        supplier.setId(supplierId);
        invoice.setSupplier(supplier);

        invoice.setDepartment(resolveDepartment(invoice.getDepartment()));
        populateSupplierFields(invoice);

        invoice.setId(null);
        invoice.setReferenceNumber(referenceNumberGenerator.nextReferenceNumber());
        invoice.setStatus(InvoiceStatus.BROUILLON);
        invoice.setDeletedAt(null);
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getSupplierInvoiceStatusCounts(UUID supplierId) {
        java.util.List<Object[]> results = invoiceRepository.countInvoicesByStatusForSupplier(supplierId);
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        for (Object[] r : results) {
            InvoiceStatus s = (InvoiceStatus) r[0];
            Long count = (Long) r[1];
            map.put(s.name(), count);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getSupplierInvoiceMatchingStatusCounts(UUID supplierId) {
        java.util.List<Object[]> results = invoiceRepository.countInvoicesByMatchingStatusForSupplier(supplierId);
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        for (Object[] r : results) {
            String matchingStatus = (String) r[0];
            Long count = (Long) r[1];
            map.put(matchingStatus != null ? matchingStatus : "PENDING", count);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public java.time.LocalDate getSupplierNextExpectedPaymentDate(UUID supplierId) {
        return invoiceRepository.findNextExpectedPaymentDateForSupplier(supplierId);
    }

    /**
     * Non-blocking, advisory duplicate pre-check used while an invoice is being entered.
     *
     * <p>Mirrors the look-back window of the blocking submission-time check (same supplier and
     * description over the last 365 days, excluding rejected/archived invoices) but never throws:
     * the UI calls it to surface a warning before the user submits. Returns "not duplicate" when
     * the input is incomplete (no supplier or blank description) so that partially-filled forms do
     * not trigger spurious warnings.</p>
     *
     * @param supplierId  the supplier being invoiced (may be {@code null} during entry)
     * @param description the invoice description / number being entered (may be blank during entry)
     * @return a {@link com.oct.invoicesystem.domain.invoice.dto.DuplicateCheckDTO} with the match count
     */
    @Transactional(readOnly = true)
    public com.oct.invoicesystem.domain.invoice.dto.DuplicateCheckDTO checkDuplicate(
            UUID supplierId, String description) {
        if (supplierId == null || description == null || description.isBlank()) {
            return new com.oct.invoicesystem.domain.invoice.dto.DuplicateCheckDTO(false, 0L);
        }
        java.time.Instant since = java.time.Instant.now().minus(365, java.time.temporal.ChronoUnit.DAYS);
        long count = invoiceRepository.countDuplicatesBySupplierAndDescription(supplierId, description, since);
        return new com.oct.invoicesystem.domain.invoice.dto.DuplicateCheckDTO(count > 0, count);
    }
}
