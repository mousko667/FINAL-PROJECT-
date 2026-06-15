package com.oct.invoicesystem.domain.report.service;

import com.oct.invoicesystem.domain.audit.repository.AuditLogRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.report.dto.ReportDefinitionDTO;
import com.oct.invoicesystem.domain.report.model.ReportDefinition;
import com.oct.invoicesystem.domain.report.repository.ReportDefinitionRepository;
import com.oct.invoicesystem.domain.supplier.service.SupplierService;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.export.TabularExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Custom report builder (M11): persist named report definitions, run them on demand (producing a
 * CSV/Excel/PDF file from the chosen dataset), and support scheduled distribution.
 */
@Service
@RequiredArgsConstructor
public class ReportBuilderService {

    private static final Set<String> DATASETS = Set.of("INVOICES", "SUPPLIERS", "AUDIT", "BUDGET");
    private static final Set<String> FREQUENCIES = Set.of("MANUAL", "DAILY", "WEEKLY", "MONTHLY");

    private final ReportDefinitionRepository repository;
    private final TabularExportService exportService;
    private final InvoiceService invoiceService;
    private final SupplierService supplierService;
    private final AuditLogRepository auditLogRepository;
    private final ReportService reportService;

    @Transactional(readOnly = true)
    public List<ReportDefinitionDTO.Response> list() {
        return repository.findByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public ReportDefinitionDTO.Response create(ReportDefinitionDTO.Request req, UUID actorId) {
        String dataset = upperOrThrow(req.dataset(), DATASETS, "dataset");
        String format = req.format() == null || req.format().isBlank() ? "CSV"
                : upperOrThrow(req.format(), Set.of("CSV", "EXCEL", "PDF"), "format");
        String frequency = req.frequency() == null || req.frequency().isBlank() ? "MANUAL"
                : upperOrThrow(req.frequency(), FREQUENCIES, "frequency");
        ReportDefinition def = ReportDefinition.builder()
                .name(req.name()).dataset(dataset).format(format).frequency(frequency)
                .recipients(req.recipients()).active(true).createdBy(actorId).build();
        return toDto(repository.save(def));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Report definition not found: " + id);
        repository.deleteById(id);
    }

    /** Runs a definition and returns the rendered file bytes (does not distribute). */
    @Transactional
    public byte[] run(UUID id) {
        ReportDefinition def = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report definition not found: " + id));
        byte[] bytes = render(def);
        def.setLastRunAt(Instant.now());
        repository.save(def);
        return bytes;
    }

    public TabularExportService.Format formatOf(UUID id) {
        ReportDefinition def = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report definition not found: " + id));
        return TabularExportService.Format.from(def.getFormat());
    }

    /** Renders the report's dataset into the chosen format. Used by run() and the scheduler. */
    public byte[] render(ReportDefinition def) {
        TabularExportService.Format fmt = TabularExportService.Format.from(def.getFormat());
        return switch (def.getDataset()) {
            case "INVOICES" -> exportService.export(fmt, "Invoices",
                    List.of("Reference", "Supplier", "Amount", "Currency", "Status", "Issue date", "Due date", "Department"),
                    invoiceService.buildExportRows(null, null, null, null, null));
            case "SUPPLIERS" -> exportService.export(fmt, "Suppliers",
                    List.of("Company", "Tax ID", "Email", "Phone", "Status"),
                    supplierService.searchSuppliers(null, null, null, Pageable.unpaged()).getContent().stream()
                            .map(s -> List.of(ns(s.companyName()), ns(s.taxId()), ns(s.contactEmail()),
                                    ns(s.contactPhone()), s.status() == null ? "" : s.status().name())).toList());
            case "BUDGET" -> exportService.export(fmt, "Budget vs Actual",
                    List.of("Department", "Budget", "Actual", "Variance", "Utilization %"),
                    reportService.getBudgetVsActual().lines().stream()
                            .map(l -> List.of(ns(l.departmentCode()),
                                    l.budget() == null ? "" : l.budget().toPlainString(),
                                    l.actual() == null ? "" : l.actual().toPlainString(),
                                    l.variance() == null ? "" : l.variance().toPlainString(),
                                    l.utilizationPercent() == null ? "" : l.utilizationPercent().toPlainString())).toList());
            case "AUDIT" -> exportService.export(fmt, "Audit",
                    List.of("Date", "Action", "Entity", "Entity ID", "IP"),
                    auditLogRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 5000,
                            Sort.by(Sort.Direction.DESC, "createdAt"))).getContent().stream()
                            .map(a -> List.of(a.getCreatedAt() == null ? "" : a.getCreatedAt().toString(),
                                    ns(a.getAction()), ns(a.getEntityType()), ns(a.getEntityId()), ns(a.getIpAddress()))).toList());
            default -> throw new ValidationException("Unknown dataset: " + def.getDataset());
        };
    }

    /**
     * Executive summary (M11): a one-page PDF aggregating the headline KPIs + budget totals.
     */
    @Transactional(readOnly = true)
    public byte[] executiveSummaryPdf() {
        var kpi = reportService.getDashboardKpis();
        var budget = reportService.getBudgetVsActual();
        List<List<String>> rows = List.of(
                List.of("Total invoices", String.valueOf(kpi.totalInvoices())),
                List.of("Overdue invoices", String.valueOf(kpi.overdueCount())),
                List.of("Avg processing time (days)", String.format("%.1f", kpi.averageProcessingTimeDays())),
                List.of("Rejection rate", String.format("%.1f%%", kpi.rejectionRate() * 100)),
                List.of("Total budget", budget.totalBudget() == null ? "0" : budget.totalBudget().toPlainString()),
                List.of("Total committed spend", budget.totalActual() == null ? "0" : budget.totalActual().toPlainString()),
                List.of("Webhook delivery success", String.format("%.0f%%", kpi.webhookDeliverySuccessRate() * 100)));
        return exportService.export(TabularExportService.Format.PDF, "Executive Summary",
                List.of("Indicator", "Value"), rows);
    }

    private String upperOrThrow(String v, Set<String> allowed, String field) {
        String u = v.trim().toUpperCase();
        if (!allowed.contains(u)) throw new ValidationException("Invalid " + field + ": " + v);
        return u;
    }

    private String ns(String s) { return s == null ? "" : s; }

    private ReportDefinitionDTO.Response toDto(ReportDefinition d) {
        return new ReportDefinitionDTO.Response(d.getId(), d.getName(), d.getDataset(), d.getFormat(),
                d.getFrequency(), d.getRecipients(), d.isActive(), d.getCreatedAt(), d.getLastRunAt());
    }
}
