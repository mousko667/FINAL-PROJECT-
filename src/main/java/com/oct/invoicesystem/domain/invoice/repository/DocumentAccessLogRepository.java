package com.oct.invoicesystem.domain.invoice.repository;

import com.oct.invoicesystem.domain.invoice.model.DocumentAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentAccessLogRepository extends JpaRepository<DocumentAccessLog, UUID> {

    List<DocumentAccessLog> findByDocumentIdOrderByAccessedAtDesc(UUID documentId);

    List<DocumentAccessLog> findByInvoiceIdOrderByAccessedAtDesc(UUID invoiceId);
}
