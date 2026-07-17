package com.oct.invoicesystem.domain.report.service;

import com.oct.invoicesystem.domain.audit.repository.AuditLogRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.report.dto.ReportDefinitionDTO;
import com.oct.invoicesystem.domain.report.model.ReportDefinition;
import com.oct.invoicesystem.domain.report.repository.ReportDefinitionRepository;
import com.oct.invoicesystem.domain.supplier.service.SupplierService;
import com.oct.invoicesystem.domain.report.dto.ReportPreviewDTO;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.export.TabularExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportBuilderServiceTest {

    @Mock private ReportDefinitionRepository repository;
    @Mock private InvoiceService invoiceService;
    @Mock private SupplierService supplierService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ReportService reportService;
    @Mock private org.springframework.context.MessageSource messageSource;
    @Mock private com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;

    // Spy on a real export service so PDF/CSV bytes are genuinely generated while still allowing
    // verify()/ArgumentCaptor on the call args (a plain @Mock would return null bytes).
    private final TabularExportService exportService = org.mockito.Mockito.spy(new TabularExportService());

    private ReportBuilderService service() {
        org.mockito.Mockito.lenient().when(messageSource.getMessage(any(String.class), any(), any(java.util.Locale.class)))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    int lastDot = key.lastIndexOf('.');
                    String suffix = lastDot >= 0 ? key.substring(lastDot + 1) : key;
                    suffix = suffix.replace("_", " ");
                    return suffix.substring(0, 1).toUpperCase() + suffix.substring(1);
                });
        org.mockito.Mockito.lenient().when(messageSource.getMessage(any(String.class), any(), any(String.class), any(java.util.Locale.class)))
                .thenAnswer(inv -> inv.getArgument(2));
        // INVOICES dataset now delegates its header list to the shared invoice-export source of truth.
        org.mockito.Mockito.lenient().when(invoiceService.invoiceExportHeaders(any(), any()))
                .thenReturn(List.of("Reference", "Supplier", "Supplier email", "Amount", "Currency",
                        "Status", "Department", "Issue date", "Due date", "Created at", "Matching status"));
        return new ReportBuilderService(repository, exportService, invoiceService,
                supplierService, auditLogRepository, reportService, messageSource, securityHelper);
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
        when(invoiceService.buildExportRows(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(List.of("FAC-1", "ACME", "100", "XAF", "VALIDE", "2026-01-01", "2026-02-01", "FIN")));
        ReportDefinition def = ReportDefinition.builder().name("Inv").dataset("INVOICES").format("CSV").build();
        byte[] bytes = service().render(def, null);
        assertTrue(bytes.length > 0);
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("FAC-1"));
        assertTrue(csv.startsWith("Reference,Supplier"));
    }

    private ReportDefinition invoicesDef() {
        return ReportDefinition.builder().name("Inv").dataset("INVOICES").format("CSV").build();
    }

    @Test
    void preview_truncatesRowsToLimit_butReportsTrueTotal() {
        UUID id = UUID.randomUUID();
        ReportDefinition def = invoicesDef();
        when(repository.findById(id)).thenReturn(Optional.of(def));
        when(invoiceService.buildExportRows(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                List.of("FAC-1", "ACME", "100", "XAF", "VALIDE", "2026-01-01", "2026-02-01", "FIN"),
                List.of("FAC-2", "BETA", "200", "XAF", "VALIDE", "2026-01-02", "2026-02-02", "FIN"),
                List.of("FAC-3", "GAMMA", "300", "XAF", "VALIDE", "2026-01-03", "2026-02-03", "FIN")));

        ReportPreviewDTO p = service().preview(id, 2);

        assertEquals(2, p.rows().size());
        assertEquals(3, p.totalRows());
        assertEquals("FAC-1", p.rows().get(0).get(0));
        assertTrue(p.columns().contains("Reference"));
        assertEquals("INVOICES", p.dataset());
    }

    @Test
    void preview_limitAboveTotal_returnsAllRows() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(invoicesDef()));
        when(invoiceService.buildExportRows(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                List.of("FAC-1", "ACME", "100", "XAF", "VALIDE", "2026-01-01", "2026-02-01", "FIN")));

        ReportPreviewDTO p = service().preview(id, 50);

        assertEquals(1, p.rows().size());
        assertEquals(1, p.totalRows());
    }

    @Test
    void preview_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service().preview(id, 20));
    }

    @Test
    void preview_doesNotStampLastRunAt() {
        UUID id = UUID.randomUUID();
        ReportDefinition def = invoicesDef();
        when(repository.findById(id)).thenReturn(Optional.of(def));
        when(invoiceService.buildExportRows(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(List.of("FAC-1", "ACME", "100", "XAF", "VALIDE", "2026-01-01", "2026-02-01", "FIN")));

        service().preview(id, 20);

        assertNull(def.getLastRunAt());
        verify(repository, never()).save(any());
    }

    @Test
    void executiveSummaryPdf_passesMetadataToExport() {
        com.oct.invoicesystem.domain.user.model.Role role =
                com.oct.invoicesystem.domain.user.model.Role.builder().name("ROLE_DAF").build();
        com.oct.invoicesystem.domain.user.model.UserRole ur =
                com.oct.invoicesystem.domain.user.model.UserRole.builder().role(role).build();
        com.oct.invoicesystem.domain.user.model.User user =
                com.oct.invoicesystem.domain.user.model.User.builder()
                        .firstName("Jean").lastName("Dupont")
                        .userRoles(new java.util.HashSet<>(java.util.Set.of(ur)))
                        .build();
        org.springframework.security.core.Authentication auth =
                org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
        org.mockito.Mockito.when(securityHelper.currentUser(auth)).thenReturn(user);

        org.mockito.Mockito.when(reportService.getDashboardKpis())
                .thenReturn(new com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO(
                        0L, java.util.Map.of(), 0.0, 0.0, 0L, java.util.Map.of(), 0.0, 0.0, 0.0, 0.0, java.util.Map.of()));
        org.mockito.Mockito.when(reportService.getBudgetVsActual())
                .thenReturn(new com.oct.invoicesystem.domain.report.dto.BudgetVsActualDTO(
                        java.util.List.of(), null, null));

        ReportBuilderService svc = service();
        byte[] out = svc.executiveSummaryPdf(auth);
        assertTrue(out != null && out.length > 0);

        org.mockito.ArgumentCaptor<com.oct.invoicesystem.shared.export.ReportMetadata> cap =
                org.mockito.ArgumentCaptor.forClass(com.oct.invoicesystem.shared.export.ReportMetadata.class);
        org.mockito.Mockito.verify(exportService).export(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                cap.capture(), org.mockito.ArgumentMatchers.any());
        assertEquals("Dupont Jean", cap.getValue().generatorName());
        assertNull(cap.getValue().periodLabel());
    }
}
