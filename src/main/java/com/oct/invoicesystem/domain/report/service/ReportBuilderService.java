package com.oct.invoicesystem.domain.report.service;

import com.oct.invoicesystem.domain.audit.repository.AuditLogRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.report.dto.ReportDefinitionDTO;
import com.oct.invoicesystem.domain.report.dto.ReportPreviewDTO;
import com.oct.invoicesystem.domain.report.model.ReportDefinition;
import com.oct.invoicesystem.domain.report.repository.ReportDefinitionRepository;
import com.oct.invoicesystem.domain.supplier.service.SupplierService;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.export.ReportMetadata;
import com.oct.invoicesystem.shared.export.TabularExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
    private static final int MAX_PREVIEW_ROWS = 100;

    /** Column headers + data rows for a definition, before serialization to a file format. */
    public record Dataset(String title, List<String> columns, List<List<String>> rows) {}

    private final ReportDefinitionRepository repository;
    private final TabularExportService exportService;
    private final InvoiceService invoiceService;
    private final SupplierService supplierService;
    private final AuditLogRepository auditLogRepository;
    private final ReportService reportService;
    private final org.springframework.context.MessageSource messageSource;
    private final com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;

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
    public byte[] run(UUID id, Authentication authentication) {
        ReportDefinition def = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report definition not found: " + id));
        byte[] bytes = render(def, authentication);
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
    public byte[] render(ReportDefinition def, Authentication authentication) {
        TabularExportService.Format fmt = TabularExportService.Format.from(def.getFormat());
        Dataset ds = buildDataset(def);
        if (fmt == TabularExportService.Format.PDF && authentication != null) {
            ReportMetadata meta = builderMetadata(authentication, org.springframework.context.i18n.LocaleContextHolder.getLocale());
            return exportService.export(fmt, ds.title(), ds.columns(), ds.rows(), meta, messageSource);
        }
        return exportService.export(fmt, ds.title(), ds.columns(), ds.rows());
    }

    /** Report metadata for builder PDFs: "LASTNAME Firstname" + localized role, no period. */
    private ReportMetadata builderMetadata(Authentication authentication, Locale locale) {
        return ReportMetadata.of(securityHelper.currentUser(authentication), messageSource, null, locale);
    }

    /** Builds the headers + rows for a definition's dataset. Shared by render() and preview(). */
    public Dataset buildDataset(ReportDefinition def) {
        java.util.Locale locale = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        return switch (def.getDataset()) {
            // INVOICES reuses the single invoice-export source of truth (headers + rows), so the
            // builder and the direct /reports/export/excel can never diverge (11 columns, translated status).
            case "INVOICES" -> new Dataset("Invoices",
                    invoiceService.invoiceExportHeaders(messageSource, locale),
                    invoiceService.buildExportRows(null, null, null, null, null, messageSource, locale));
            // SUPPLIERS matches the SupplierController /suppliers/export layout exactly (7 columns,
            // incl. address + category), so both supplier exports are identical.
            case "SUPPLIERS" -> new Dataset("Suppliers",
                    List.of(
                            messageSource.getMessage("report.excel.header.company", null, locale),
                            messageSource.getMessage("report.excel.header.tax_id", null, locale),
                            messageSource.getMessage("report.excel.header.email", null, locale),
                            messageSource.getMessage("report.excel.header.phone", null, locale),
                            messageSource.getMessage("report.excel.header.address", null, locale),
                            messageSource.getMessage("report.excel.header.status", null, locale),
                            messageSource.getMessage("report.excel.header.category", null, locale)
                    ),
                    supplierService.searchSuppliers(null, null, null, null, Pageable.unpaged()).getContent().stream()
                            .map(s -> List.of(ns(s.companyName()), ns(s.taxId()), ns(s.contactEmail()),
                                    ns(s.contactPhone()), ns(s.address()),
                                    s.status() == null ? "" : s.status().name(),
                                    s.category() == null ? "" : s.category().name())).toList());
            // BUDGET adds the department name (localized) next to its code — the data already exists
            // in BudgetVsActualDTO but was dropped from the export.
            case "BUDGET" -> new Dataset("Budget vs Actual",
                    List.of(
                            messageSource.getMessage("report.excel.header.department", null, locale),
                            messageSource.getMessage("report.excel.header.name", null, locale),
                            messageSource.getMessage("report.excel.header.budget", null, locale),
                            messageSource.getMessage("report.excel.header.actual", null, locale),
                            messageSource.getMessage("report.excel.header.variance", null, locale),
                            messageSource.getMessage("report.excel.header.utilization_percent", null, locale)
                    ),
                    reportService.getBudgetVsActual().lines().stream()
                            .map(l -> List.of(ns(l.departmentCode()),
                                    ns(locale.getLanguage().startsWith("fr") ? l.nameFr() : l.nameEn()),
                                    l.budget() == null ? "" : l.budget().toPlainString(),
                                    l.actual() == null ? "" : l.actual().toPlainString(),
                                    l.variance() == null ? "" : l.variance().toPlainString(),
                                    l.utilizationPercent() == null ? "" : l.utilizationPercent().toPlainString())).toList());
            case "AUDIT" -> new Dataset("Audit",
                    List.of(
                            messageSource.getMessage("report.excel.header.date", null, locale),
                            messageSource.getMessage("report.excel.header.action", null, locale),
                            messageSource.getMessage("report.excel.header.entity", null, locale),
                            messageSource.getMessage("report.excel.header.entity_id", null, locale),
                            messageSource.getMessage("report.excel.header.ip", null, locale)
                    ),
                    auditLogRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 5000,
                            Sort.by(Sort.Direction.DESC, "createdAt"))).getContent().stream()
                            .map(a -> List.of(a.getCreatedAt() == null ? "" : a.getCreatedAt().toString(),
                                    ns(a.getAction()), ns(a.getEntityType()), ns(a.getEntityId()), ns(a.getIpAddress()))).toList());
            default -> throw new ValidationException("Unknown dataset: " + def.getDataset());
        };
    }

    /** Read-only preview of a definition's dataset, truncated to {@code limit} rows. Does NOT stamp lastRunAt. */
    @Transactional(readOnly = true)
    public ReportPreviewDTO preview(UUID id, int limit) {
        ReportDefinition def = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report definition not found: " + id));
        int capped = Math.max(1, Math.min(limit, MAX_PREVIEW_ROWS));
        Dataset ds = buildDataset(def);
        List<List<String>> truncated = ds.rows().size() > capped ? ds.rows().subList(0, capped) : ds.rows();
        return new ReportPreviewDTO(ds.columns(), List.copyOf(truncated), ds.rows().size(),
                def.getDataset(), def.getFormat());
    }

    /**
     * Executive summary (M11): a one-page PDF aggregating the headline KPIs + budget totals.
     */
    @Transactional(readOnly = true)
    public byte[] executiveSummaryPdf(Authentication authentication) {
        var kpi = reportService.getDashboardKpis();
        var budget = reportService.getBudgetVsActual();
        Locale locale = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        List<List<String>> rows = List.of(
                List.of(messageSource.getMessage("report.excel.header.total_invoices", null, locale), String.valueOf(kpi.totalInvoices())),
                List.of(messageSource.getMessage("report.excel.header.overdue_invoices", null, locale), String.valueOf(kpi.overdueCount())),
                List.of(messageSource.getMessage("report.excel.header.avg_processing_time_days", null, locale), String.format("%.1f", kpi.averageProcessingTimeDays())),
                List.of(messageSource.getMessage("report.excel.header.rejection_rate", null, locale), String.format("%.1f%%", kpi.rejectionRate() * 100)),
                List.of(messageSource.getMessage("report.excel.header.total_budget", null, locale), budget.totalBudget() == null ? "0" : budget.totalBudget().toPlainString()),
                List.of(messageSource.getMessage("report.excel.header.total_committed_spend", null, locale), budget.totalActual() == null ? "0" : budget.totalActual().toPlainString()),
                List.of(messageSource.getMessage("report.excel.header.webhook_delivery_success", null, locale), String.format("%.0f%%", kpi.webhookDeliverySuccessRate() * 100)));
        ReportMetadata meta = builderMetadata(authentication, locale);
        String title = messageSource.getMessage("report.pdf.executive.title", null, locale);
        return exportService.export(TabularExportService.Format.PDF, title,
                List.of(
                        messageSource.getMessage("report.excel.header.indicator", null, locale),
                        messageSource.getMessage("report.excel.header.value", null, locale)
                ), rows, meta, messageSource);
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
