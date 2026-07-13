package com.oct.invoicesystem.shared.export;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.context.MessageSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared rendering of the OCT report metadata: a header block (optional period, generator name +
 * role, generation date) placed under the title, and a bordered signature box placed at the
 * bottom. Centralized so every PDF report (audit, compliance, executive summary, custom builder)
 * looks identical. Never fails report generation on a missing key — callers pass a resolved
 * {@link ReportMetadata}.
 */
public final class PdfMetadata {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private PdfMetadata() {}

    /** Header block under the title: optional period (centered), optional filters (centered), then generator + date (right). */
    public static void renderHeader(Document doc, ReportMetadata meta, MessageSource ms, Locale loc) {
        if (meta == null) {
            return;
        }
        if (meta.periodLabel() != null) {
            doc.add(new Paragraph(meta.periodLabel())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(12));
        }
        if (meta.filtersLabel() != null) {
            String filtersLine = ms.getMessage("report.pdf.filters",
                    new Object[]{meta.filtersLabel()}, meta.filtersLabel(), loc);
            doc.add(new Paragraph(filtersLine)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(9)
                    .setItalic());
        }
        String generatedBy = ms.getMessage("report.pdf.generated_by",
                new Object[]{meta.generatorName(), meta.generatorRole()}, loc);
        doc.add(new Paragraph(generatedBy)
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(8));

        String generatedAt = ms.getMessage("report.pdf.generated_at",
                new Object[]{DATE_FORMAT.format(meta.generatedAt() == null ? Instant.now() : meta.generatedAt())}, loc);
        doc.add(new Paragraph(generatedAt)
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(8));
    }

    /** Bottom-of-page bordered box with a blank area to sign plus name/function and date lines. */
    public static void renderSignatureBlock(Document doc, MessageSource ms, Locale loc) {
        doc.add(new Paragraph("\n"));
        Table box = new Table(UnitValue.createPercentArray(new float[]{100})).useAllAvailableWidth();
        Cell cell = new Cell()
                .setBorder(new SolidBorder(ColorConstants.GRAY, 1f))
                .setHeight(90f)
                .add(new Paragraph(ms.getMessage("report.pdf.signature", null, loc))
                        .setBold().setFontSize(10))
                .add(new Paragraph("\n"))
                .add(new Paragraph(ms.getMessage("export.pdf.signature.name", null, "Name / function:", loc))
                        .setFontSize(9))
                .add(new Paragraph(ms.getMessage("report.pdf.signature.date", null, loc))
                        .setFontSize(9));
        box.addCell(cell);
        doc.add(box);
    }
}
