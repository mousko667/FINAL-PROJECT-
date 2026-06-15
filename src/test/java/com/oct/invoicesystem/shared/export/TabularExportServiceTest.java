package com.oct.invoicesystem.shared.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabularExportServiceTest {

    private final TabularExportService service = new TabularExportService();

    @Test
    void format_from_mapsAliases() {
        assertEquals(TabularExportService.Format.CSV, TabularExportService.Format.from(null));
        assertEquals(TabularExportService.Format.CSV, TabularExportService.Format.from("csv"));
        assertEquals(TabularExportService.Format.EXCEL, TabularExportService.Format.from("excel"));
        assertEquals(TabularExportService.Format.EXCEL, TabularExportService.Format.from("xlsx"));
        assertEquals(TabularExportService.Format.PDF, TabularExportService.Format.from("pdf"));
    }

    @Test
    void csv_quotesCommasAndGuardsFormulaInjection() {
        byte[] bytes = service.export(TabularExportService.Format.CSV, "T",
                List.of("name", "note"),
                List.of(List.of("Acme, Inc", "=SUM(A1)")));
        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"Acme, Inc\""), "comma value must be quoted");
        assertTrue(csv.contains("'=SUM(A1)"), "formula must be prefixed with a quote");
        assertTrue(csv.startsWith("name,note"), "header row first");
    }

    @Test
    void excel_producesNonEmptyWorkbook() {
        byte[] bytes = service.export(TabularExportService.Format.EXCEL, "Users",
                List.of("a", "b"), List.of(List.of("1", "2")));
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        // XLSX is a zip: starts with "PK"
        assertEquals('P', (char) bytes[0]);
        assertEquals('K', (char) bytes[1]);
    }

    @Test
    void pdf_producesNonEmptyDocument() {
        byte[] bytes = service.export(TabularExportService.Format.PDF, "Report",
                List.of("a", "b"), List.of(List.of("1", "2")));
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        // PDF magic header "%PDF"
        String head = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", head);
    }
}
