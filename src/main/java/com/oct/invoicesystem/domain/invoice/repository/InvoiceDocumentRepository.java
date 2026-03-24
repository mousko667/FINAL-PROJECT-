package com.oct.invoicesystem.domain.invoice.repository;

import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceDocumentRepository extends JpaRepository<InvoiceDocument, UUID> {
    List<InvoiceDocument> findByInvoiceId(UUID invoiceId);
    Optional<InvoiceDocument> findByIdAndInvoiceId(UUID id, UUID invoiceId);
}
