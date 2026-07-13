package com.oct.invoicesystem.shared.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
                            "DUPONT Jean", "DAF", java.time.Instant.now(), null, null);
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
    void columnWeightsSumTo100() {
        float[] w = TabularExportService.columnWeights(
                List.of("Reference", "Supplier Email", "Amount", "Status"));
        float sum = 0;
        for (float f : w) sum += f;
        assertThat(sum).isCloseTo(100f, within(0.5f));
    }

    @Test
    void wideColumnsGetMoreWeightThanNarrow() {
        List<String> headers = List.of("Amount", "Supplier Email");
        float[] w = TabularExportService.columnWeights(headers);
        // "Supplier Email" (wide) must be wider than "Amount" (narrow).
        assertThat(w[1]).isGreaterThan(w[0]);
    }

    @Test
    void unknownHeadersFallBackToEqualWeights() {
        float[] w = TabularExportService.columnWeights(List.of("Aaa", "Bbb", "Ccc"));
        assertThat(w[0]).isCloseTo(w[1], within(0.01f));
        assertThat(w[1]).isCloseTo(w[2], within(0.01f));
    }

    @Test
    void pdfIsValidAndContainsSignatureAndGeneratorWhenMetaPresent() throws Exception {
        TabularExportService svc = new TabularExportService();
        org.springframework.context.support.StaticMessageSource ms =
                new org.springframework.context.support.StaticMessageSource();
        ms.addMessage("report.pdf.generated_by", java.util.Locale.FRENCH, "Genere par {0} ({1})");
        ms.addMessage("report.pdf.generated_at", java.util.Locale.FRENCH, "Genere le {0}");
        ms.addMessage("report.pdf.signature", java.util.Locale.FRENCH, "Signature");
        ms.addMessage("report.pdf.signature.date", java.util.Locale.FRENCH, "Date :");
        ms.addMessage("export.pdf.signature.name", java.util.Locale.FRENCH, "Nom / fonction :");
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.FRENCH);
        try {
            ReportMetadata meta = new ReportMetadata("Dubois Marie", "DAF",
                    java.time.Instant.now(), null, "Statut: Draft");
            byte[] pdf = svc.export(TabularExportService.Format.PDF, "Factures",
                    java.util.List.of("Reference", "Amount"),
                    java.util.List.of(java.util.List.of("FAC-1", "1000")),
                    meta, ms);

            assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

            String text = extractPdfText(pdf);
            assertThat(text).contains("Dubois Marie");
            assertThat(text).contains("Signature");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    private static String extractPdfText(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
        }
    }
}
