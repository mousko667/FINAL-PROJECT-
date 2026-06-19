package com.oct.invoicesystem.domain.payment.repository;

import com.oct.invoicesystem.domain.payment.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByInvoiceId(UUID invoiceId);
    boolean existsByInvoiceId(UUID invoiceId);
    Page<Payment> findByInvoiceDepartmentCode(String departmentCode, Pageable pageable);
    List<Payment> findByInvoiceDepartmentCode(String departmentCode);
}
