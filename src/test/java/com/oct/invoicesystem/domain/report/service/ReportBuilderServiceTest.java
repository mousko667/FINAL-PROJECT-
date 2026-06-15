package com.oct.invoicesystem.domain.report.service;

import com.oct.invoicesystem.domain.audit.repository.AuditLogRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.report.dto.ReportDefinitionDTO;
import com.oct.invoicesystem.domain.report.model.ReportDefinition;
import com.oct.invoicesystem.domain.report.repository.ReportDefinitionRepository;
import com.oct.invoicesystem.domain.supplier.service.SupplierService;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.export.TabularExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportBuilderServiceTest {

    @Mock private ReportDefinitionRepository repository;
    @Mock private InvoiceService invoiceService;
    @Mock private SupplierService supplierService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ReportService reportService;

    // Real export service (no need to mock byte generation)
    private final TabularExportService exportService = new TabularExportService();

    private ReportBuilderService service() {
        return new ReportBuilderService(repository, exportService, invoiceService,
                supplierService, auditLogRepository, reportService);
    }

    @Test
    void create_rejectsUnknownDataset() {
        assertThrows(ValidationException.class, () -> service().create(
                new ReportDefinitionDTO.Request("R", "ORDERS", "CSV", "MANUAL", null), null));
    }

    @Test
    void create_defaultsFormatAndFrequency() {
        when(repository.save(any(ReportDefinition.class))).thenAnswer(i -> i.getArgument(0));
        var dto = service().create(new ReportDefinitionDTO.Request("R", "invoices", null, null, null), null);
        assertEquals("INVOICES", dto.dataset());
        assertEquals("CSV", dto.format());
        assertEquals("MANUAL", dto.frequency());
    }

    @Test
    void render_invoicesDataset_producesCsvBytes() {
        when(invoiceService.buildExportRows(any(), any(), any(), any(), any()))
                .thenReturn(List.of(List.of("FAC-1", "ACME", "100", "XOF", "VALIDE", "2026-01-01", "2026-02-01", "FIN")));
        ReportDefinition def = ReportDefinition.builder().name("Inv").dataset("INVOICES").format("CSV").build();
        byte[] bytes = service().render(def);
        assertTrue(bytes.length > 0);
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("FAC-1"));
        assertTrue(csv.startsWith("Reference,Supplier"));
    }
}
