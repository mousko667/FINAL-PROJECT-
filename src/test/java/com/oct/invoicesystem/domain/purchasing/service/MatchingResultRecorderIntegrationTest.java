package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderStatus;
import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUDIT-034 — behavioural proof that a matching result outlives the rollback that rejects it.
 *
 * <p>Runtime evidence of the finding: {@code three_way_matching_results} stayed at 0 rows after four
 * MISMATCH submissions, because the {@code WorkflowException} blocking the submission rolled back
 * the {@code save} that had just run. This test reproduces that exact shape — write the result, then
 * roll the surrounding transaction back — and asserts the row is still there afterwards.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class MatchingResultRecorderIntegrationTest {

    @Autowired private MatchingResultRecorder recorder;
    @Autowired private ThreeWayMatchingResultRepository resultRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private com.oct.invoicesystem.domain.user.repository.UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void record_survivesTheRollbackOfTheRejectingTransaction() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Supplier supplier = supplierRepository.save(Supplier.builder()
                .companyName("Recorder Co " + tag)
                .taxId("TAX-REC-" + tag)
                .contactEmail("rec." + tag + "@example.com")
                .status(SupplierStatus.ACTIVE)
                .build());
        Department department = departmentRepository.save(Department.builder()
                .nameFr("Recorder Dept " + tag)
                .nameEn("Recorder Dept " + tag)
                .code("REC" + tag.substring(0, 4).toUpperCase())
                .requiresN2(false)
                .n1Role("ROLE_VALIDATEUR_N1_DRH")
                .build());
        com.oct.invoicesystem.domain.user.model.User submitter = userRepository.save(
                com.oct.invoicesystem.domain.user.model.User.builder()
                        .username("recorder-" + tag)
                        .email("recorder." + tag + "@example.com")
                        .password("x")
                        .firstName("Rec")
                        .lastName("Order")
                        .active(true)
                        .build());
        PurchaseOrder po = purchaseOrderRepository.save(PurchaseOrder.builder()
                .poNumber("PO-REC-" + tag)
                .supplier(supplier)
                .status(PurchaseOrderStatus.OPEN)
                .totalAmount(new BigDecimal("1000.00"))
                .createdBy(submitter)
                .build());
        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .referenceNumber("FAC-REC-" + tag.substring(0, 6))
                .department(department)
                .submittedBy(submitter)
                .supplier(supplier)
                .supplierName("Recorder Co " + tag)
                .supplierEmail("rec." + tag + "@example.com")
                .amount(new BigDecimal("1000.00"))
                .currency("XAF")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .status(InvoiceStatus.BROUILLON)
                .build());

        long before = resultRepository.count();

        // Same shape as performMatchingCheck: record the MISMATCH, then blow the transaction up —
        // which is precisely what used to erase the row.
        try {
            transactionTemplate.execute(status -> {
                recorder.record(ThreeWayMatchingResult.builder()
                        .invoice(invoice)
                        .purchaseOrder(po)
                        .status(MatchingStatus.MISMATCH)
                        .discrepancyNotes("internal notes that must never reach the API message")
                        .build());
                throw new IllegalStateException("submission blocked by MISMATCH");
            });
        } catch (IllegalStateException expected) {
            // the rejection the finding describes
        }

        assertThat(resultRepository.count())
                .as("the MISMATCH result must remain — these are exactly the cases an auditor needs")
                .isEqualTo(before + 1);
    }
}
