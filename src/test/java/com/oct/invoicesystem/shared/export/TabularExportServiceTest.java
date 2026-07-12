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
    void neutralizeFormula_guardsDangerousLeads() {
        assertEquals("'=cmd", TabularExportService.neutralizeFormula("=cmd"));
        assertEquals("'+1", TabularExportService.neutralizeFormula("+1"));
        assertEquals("'-1", TabularExportService.neutralizeFormula("-1"));
        assertEquals("'@x", TabularExportService.neutralizeFormula("@x"));
        assertEquals("safe", TabularExportService.neutralizeFormula("safe"));
        assertEquals("", TabularExportService.neutralizeFormula(null));
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

    @Test
    void pdf_embedsLetterheadLogo() {
        byte[] bytes = service.export(TabularExportService.Format.PDF, "Report",
                List.of("a", "b"), List.of(List.of("1", "2")));
        // The OCT logo is embedded as a PDF image XObject; a logo-less document has none.
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(content.contains("/XObject"),
                "PDF export should embed the OCT letterhead logo as an image XObject");
    }

    @Test
    void pdf_withoutMetadata_stillProducesPdf_backwardCompatible() {
        byte[] bytes = service.export(TabularExportService.Format.PDF, "Report",
                List.of("a", "b"), List.of(List.of("1", "2")), null);
        assertNotNull(bytes);
        String head = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", head);
    }

    @Test
    void pdf_withMetadata_isLargerThanWithout() {
        org.springframework.context.support.StaticMessageSource ms =
                new org.springframework.context.support.StaticMessageSource();
        java.util.Locale loc = java.util.Locale.FRENCH;
        ms.addMessage("report.pdf.generated_by", loc, "Genere par : {0} ({1})");
        ms.addMessage("report.pdf.generated_at", loc, "Genere le : {0}");
        ms.addMessage("report.pdf.signature", loc, "Signature :");
        ms.addMessage("report.pdf.signature.date", loc, "Date :");

        java.util.Locale prev = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        org.springframework.context.i18n.LocaleContextHolder.setLocale(loc);
        try {
            com.oct.invoicesystem.shared.export.ReportMetadata meta =
                    new com.oct.invoicesystem.shared.export.ReportMetadata(
                            "DUPONT Jean", "DAF", java.time.Instant.now(), null);
            byte[] withMeta = service.export(TabularExportService.Format.PDF, "Report",
                    List.of("a", "b"), List.of(List.of("1", "2")), meta, ms);
            byte[] withoutMeta = service.export(TabularExportService.Format.PDF, "Report",
                    List.of("a", "b"), List.of(List.of("1", "2")), null);
            assertNotNull(withMeta);
            assertEquals("%PDF", new String(withMeta, 0, 4, StandardCharsets.US_ASCII));
            assertTrue(withMeta.length > withoutMeta.length,
                    "PDF with metadata block should be larger than the plain one");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(prev);
        }
    }

    @Test
    void pdf_withMetadata_rendersHeaderRule() {
        org.springframework.context.support.StaticMessageSource ms =
                new org.springframework.context.support.StaticMessageSource();
        java.util.Locale loc = java.util.Locale.FRENCH;
        ms.addMessage("report.pdf.generated_by", loc, "Genere par : {0} ({1})");
        ms.addMessage("report.pdf.generated_at", loc, "Genere le : {0}");
        ms.addMessage("report.pdf.signature", loc, "Signature :");
        ms.addMessage("report.pdf.signature.date", loc, "Date :");

        java.util.Locale prev = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        org.springframework.context.i18n.LocaleContextHolder.setLocale(loc);
        try {
            ReportMetadata meta = new ReportMetadata(
                    "DUPONT Jean", "DAF", java.time.Instant.now(), "Periode : 2026");
            byte[] bytes = service.export(TabularExportService.Format.PDF, "Report",
                    List.of("a", "b"), List.of(List.of("1", "2")), meta, ms);
            assertNotNull(bytes);
            assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
            // Un en-tete avec metadonnees + filet de separation produit un flux PDF non trivial.
            assertTrue(bytes.length > 1000, "un rapport avec en-tete meta doit produire un flux non trivial");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(prev);
        }
    }

    @Test
    void pdf_manyRows_repeatsHeaderAcrossPages() {
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 120; i++) {
            rows.add(java.util.List.of("ligne " + i, "valeur " + i));
        }
        byte[] bytes = service.export(TabularExportService.Format.PDF, "Grand rapport",
                List.of("Libelle", "Montant"), rows);
        assertNotNull(bytes);
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        // Un document multi-pages declare plusieurs objets /Page.
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        int pageCount = content.split("/Type\\s*/Page[^s]").length - 1;
        assertTrue(pageCount >= 2, "120 lignes doivent deborder sur au moins 2 pages, vu: " + pageCount);
    }
}
