package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
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

    @Transactional(readOnly = true)
    public Page<Invoice> getPendingValidationQueue(Pageable pageable) {
        return invoiceRepository.findPendingValidationQueue(pageable);
    }

    @Transactional(readOnly = true)
    public ThreeWayMatchingResult getMatchingResult(UUID invoiceId) {
        return matchingResultRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Matching result not found for invoice: " + invoiceId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceHistoryDTO> getInvoiceHistory(UUID invoiceId) {
        getById(invoiceId); // validates existence
        return historyRepository.findHistoryDTOsByInvoiceId(invoiceId);
    }

    @Transactional(readOnly = true)
    public void validateDocumentPresent(UUID invoiceId) {
        if (invoiceDocumentRepository.findByInvoiceId(invoiceId).isEmpty()) {
            throw new ValidationException("error.invoice.no_document");
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
    public Page<Invoice> searchArchived(String keyword, UUID departmentId, Instant from, Instant to, Pageable pageable) {
        return invoiceRepository.searchArchived(keyword, departmentId, from, to, pageable);
    }

    @Transactional
    public Invoice createSupplierInvoice(Invoice invoice, UUID actorId, UUID supplierId) {
        // Enforce supplier ID override for security
        Supplier supplier = new Supplier();
        supplier.setId(supplierId);
        invoice.setSupplier(supplier);
        
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
}
