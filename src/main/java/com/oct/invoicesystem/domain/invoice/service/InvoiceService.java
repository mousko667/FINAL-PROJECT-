package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
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
    private final ReferenceNumberGenerator referenceNumberGenerator;

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

        existing.setDepartment(resolveDepartment(updatedInvoice.getDepartment()));
        existing.setSupplierName(updatedInvoice.getSupplierName());
        existing.setSupplierEmail(updatedInvoice.getSupplierEmail());
        existing.setSupplierTaxId(updatedInvoice.getSupplierTaxId());
        existing.setSupplierBankDetails(updatedInvoice.getSupplierBankDetails());
        existing.setAmount(updatedInvoice.getAmount());
        existing.setCurrency(updatedInvoice.getCurrency());
        existing.setIssueDate(updatedInvoice.getIssueDate());
        existing.setDueDate(updatedInvoice.getDueDate());
        existing.setDescription(updatedInvoice.getDescription());

        return invoiceRepository.save(existing);
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
     * @param departmentId optional department id
     * @param fromDate optional lower issue date bound
     * @param toDate optional upper issue date bound
     * @param reference optional partial reference filter
     * @param page page index
     * @param size page size
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
}
