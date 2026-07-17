package com.oct.invoicesystem.shared.export;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PdfMetadataTest {

    private byte[] buildPdf(ReportMetadata meta) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        StaticMessageSource ms = new StaticMessageSource();
        Locale loc = Locale.FRENCH;
        ms.addMessage("report.pdf.generated_by", loc, "Genere par : {0} ({1})");
        ms.addMessage("report.pdf.generated_at", loc, "Genere le : {0}");
        ms.addMessage("report.pdf.filters", loc, "Filtres : {0}");

        PdfMetadata.renderHeader(doc, "Test Title", meta, ms, loc);
        doc.close();
        return out.toByteArray();
    }

    @Test
    void filtersLabelAppearsInPdf() throws Exception {
        ReportMetadata meta = new ReportMetadata("DUPONT Jean", "DAF", Instant.now(), "2026-01", "Statut: Brouillon");
        byte[] bytes = buildPdf(meta);
        try (PDDocument pdDoc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdDoc);
            assertThat(text).contains("Statut: Brouillon");
        }
    }

    @Test
    void withoutFiltersLabelNoFiltersLineRendered() throws Exception {
        ReportMetadata meta = new ReportMetadata("DUPONT Jean", "DAF", Instant.now(), "2026-01", null);
        byte[] bytes = buildPdf(meta);
        try (PDDocument pdDoc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdDoc);
            assertThat(text).doesNotContain("Filtres");
        }
    }
}
