package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceImportResultDTO;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.ocr.dto.OcrExtractionResult;
import com.oct.invoicesystem.domain.ocr.service.InvoiceXmlParser;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Multi-invoice import (B8, M3): creates several draft (BROUILLON) invoices in one upload, from a
 * CSV (one row per invoice) or an XML document containing several {@code <invoice>} elements.
 *
 * <p>Best-effort: each invoice is created via {@link InvoiceService#createInvoice} (its own
 * transaction), so a failing entry does not roll back the others — the caller gets a per-line
 * outcome. This is distinct from the existing bulk <i>document</i> upload (several files for one
 * invoice).</p>
 *
 * <p>CSV header (in any order), one invoice per data row:
 * {@code departmentCode,supplierName,supplierEmail,supplierTaxId,amount,currency,issueDate,dueDate,description}.
 * A {@code departmentCode} column overrides the request-level department; {@code issueDate}/{@code
 * dueDate} are ISO ({@code yyyy-MM-dd}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceImportService {

    private final InvoiceService invoiceService;
    private final DepartmentRepository departmentRepository;
    private final InvoiceXmlParser invoiceXmlParser;
    private final Tika tika = new Tika();

    /**
     * @param file            the CSV or XML upload
     * @param defaultDeptCode department code applied to invoices that do not carry their own (XML,
     *                        or CSV rows without a departmentCode); required for XML.
     */
    public InvoiceImportResultDTO importInvoices(MultipartFile file, String defaultDeptCode, UUID actorId) {
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("invoice.import.unreadable_file");
        }
        // Route by content first; fall back to the filename and a leading-'<' heuristic, because Tika
        // detects a short XML snippet without a prolog as text/plain.
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        String mimeType = tika.detect(bytes, filename);
        boolean isXml = invoiceXmlParser.isXml(mimeType)
                || filename.endsWith(".xml")
                || startsWithXmlTag(bytes);
        return isXml
                ? importXml(bytes, defaultDeptCode, actorId)
                : importCsv(bytes, defaultDeptCode, actorId);
    }

    // ── CSV ──────────────────────────────────────────────────────────────────

    private InvoiceImportResultDTO importCsv(byte[] bytes, String defaultDeptCode, UUID actorId) {
        List<List<String>> rows;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            rows = parseCsv(reader);
        } catch (IOException e) {
            throw new ValidationException("invoice.import.unreadable_file");
        }
        if (rows.isEmpty()) {
            return new InvoiceImportResultDTO(0, 0, 0, List.of());
        }

        List<String> header = rows.get(0).stream().map(s -> s.trim().toLowerCase()).toList();
        List<InvoiceImportResultDTO.LineResult> results = new ArrayList<>();
        int created = 0;

        for (int i = 1; i < rows.size(); i++) {
            int line = i + 1; // header = line 1
            List<String> row = rows.get(i);
            if (row.stream().allMatch(c -> c == null || c.isBlank())) continue;
            try {
                String deptCode = orDefault(cell(header, row, "departmentcode"), defaultDeptCode);
                Invoice invoice = Invoice.builder()
                        .department(resolveDepartment(deptCode))
                        .submittedBy(actorRef(actorId))
                        .supplierName(require(cell(header, row, "suppliername"), "supplierName"))
                        .supplierEmail(require(cell(header, row, "supplieremail"), "supplierEmail"))
                        .supplierTaxId(blankToNull(cell(header, row, "suppliertaxid")))
                        .amount(parseAmount(require(cell(header, row, "amount"), "amount")))
                        .currency(orDefault(cell(header, row, "currency"), "XAF"))
                        .issueDate(parseDate(require(cell(header, row, "issuedate"), "issueDate")))
                        .dueDate(parseDate(require(cell(header, row, "duedate"), "dueDate")))
                        .description(blankToNull(cell(header, row, "description")))
                        .build();
                Invoice saved = invoiceService.createInvoice(invoice, actorId);
                results.add(InvoiceImportResultDTO.LineResult.ok(line, saved.getId(), saved.getReferenceNumber()));
                created++;
            } catch (Exception e) {
                log.warn("Invoice CSV import failed at line {}: {}", line, e.getMessage());
                results.add(InvoiceImportResultDTO.LineResult.failure(line, errorMessage(e)));
            }
        }
        return new InvoiceImportResultDTO(created + countFailed(results), created, countFailed(results), results);
    }

    // ── XML (multiple <invoice>) ───────────────────────────────────────────────

    private InvoiceImportResultDTO importXml(byte[] bytes, String defaultDeptCode, UUID actorId) {
        List<OcrExtractionResult> parsed = invoiceXmlParser.parseMany(bytes);
        List<InvoiceImportResultDTO.LineResult> results = new ArrayList<>();
        int created = 0;
        Department department;
        try {
            department = resolveDepartment(defaultDeptCode);
        } catch (Exception e) {
            // Without a valid target department no XML invoice can be created — fail the whole batch.
            for (int i = 0; i < Math.max(parsed.size(), 1); i++) {
                results.add(InvoiceImportResultDTO.LineResult.failure(i + 1, errorMessage(e)));
            }
            return new InvoiceImportResultDTO(results.size(), 0, results.size(), results);
        }

        for (int i = 0; i < parsed.size(); i++) {
            int line = i + 1;
            OcrExtractionResult x = parsed.get(i);
            try {
                // The simple OCT XML schema carries the supplier tax id, not a company name; use the
                // tax id (or invoice number) as the supplier label so the draft is identifiable.
                String supplierLabel = blankToNull(x.getSupplierId());
                if (supplierLabel == null) supplierLabel = blankToNull(x.getInvoiceNumber());
                Invoice invoice = Invoice.builder()
                        .department(department)
                        .submittedBy(actorRef(actorId))
                        .supplierName(require(supplierLabel, "supplierTaxId/number"))
                        .supplierEmail("noreply@import.local")
                        .supplierTaxId(blankToNull(x.getSupplierId()))
                        .amount(requireAmount(x.getTotalAmount()))
                        .currency("XAF")
                        .issueDate(parseDate(require(x.getInvoiceDate(), "date")))
                        .dueDate(parseDate(require(x.getInvoiceDate(), "date")))
                        .description(blankToNull(x.getInvoiceNumber()))
                        .build();
                Invoice saved = invoiceService.createInvoice(invoice, actorId);
                results.add(InvoiceImportResultDTO.LineResult.ok(line, saved.getId(), saved.getReferenceNumber()));
                created++;
            } catch (Exception e) {
                log.warn("Invoice XML import failed at entry {}: {}", line, e.getMessage());
                results.add(InvoiceImportResultDTO.LineResult.failure(line, errorMessage(e)));
            }
        }
        return new InvoiceImportResultDTO(results.size(), created, countFailed(results), results);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Department resolveDepartment(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("Department code is required");
        }
        return departmentRepository.findByCode(code.trim())
                .orElseThrow(() -> new ValidationException("Unknown department code: " + code));
    }

    private User actorRef(UUID actorId) {
        User u = new User();
        u.setId(actorId);
        return u;
    }

    private BigDecimal parseAmount(String raw) {
        return requireAmount(new BigDecimal(raw.replaceAll("[\\s,](?=\\d{3}\\b)", "").replace(",", ".")));
    }

    private BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null) throw new ValidationException("amount is required");
        return amount;
    }

    private LocalDate parseDate(String raw) {
        return LocalDate.parse(raw.trim());
    }

    private int countFailed(List<InvoiceImportResultDTO.LineResult> results) {
        return (int) results.stream().filter(r -> !r.success()).count();
    }

    private String errorMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** True if the (possibly BOM/whitespace-prefixed) content starts with '<' — an XML document. */
    private boolean startsWithXmlTag(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 64), StandardCharsets.UTF_8)
                .replace("﻿", "").stripLeading();
        return head.startsWith("<");
    }

    /** Minimal RFC-4180 CSV parse: handles quoted fields, escaped quotes ("") and embedded commas/newlines. */
    private List<List<String>> parseCsv(BufferedReader reader) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            char c = (char) ch;
            if (inQuotes) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (next == -1) break;
                        c = (char) next;
                        if (c == ',') { current.add(field.toString()); field.setLength(0); }
                        else if (c == '\n') { current.add(field.toString()); field.setLength(0); rows.add(current); current = new ArrayList<>(); }
                        else if (c != '\r') field.append(c);
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                rows.add(current);
                current = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current);
        }
        return rows;
    }

    private String cell(List<String> header, List<String> row, String key) {
        int idx = header.indexOf(key);
        return idx >= 0 && idx < row.size() ? row.get(idx) : null;
    }

    private String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required");
        }
        return value.trim();
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
