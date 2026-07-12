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
 * Shared rendering of the OCT report metadata: a header block (generator name + role, generation
 * date, optional period) aligned right under the title with a thin navy/gold separator rule, and a
 * two-line signature block (Signature / Date, no box) placed at the bottom. Centralized so every PDF
 * report (audit, compliance, executive summary, custom builder) looks identical. Never fails report
 * generation on a missing key — callers pass a resolved {@link ReportMetadata}.
 */
public final class PdfMetadata {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private PdfMetadata() {}

    private static final com.itextpdf.kernel.colors.Color NAVY =
            new com.itextpdf.kernel.colors.DeviceRgb(15, 37, 64);
    private static final com.itextpdf.kernel.colors.Color GOLD =
            new com.itextpdf.kernel.colors.DeviceRgb(200, 168, 75);

    /** Header block under the title: metadata (generator, date, optional period) aligned right,
     *  then a thin navy rule with a gold accent segment separating the header from the body. */
    public static void renderHeader(Document doc, ReportMetadata meta, MessageSource ms, Locale loc) {
        if (meta == null) {
            return;
        }
        // Metadata grouped on the right.
        String generatedBy = ms.getMessage("report.pdf.generated_by",
                new Object[]{meta.generatorName(), meta.generatorRole()}, loc);
        doc.add(new Paragraph(generatedBy)
                .setTextAlignment(TextAlignment.RIGHT).setFontSize(8));
        String generatedAt = ms.getMessage("report.pdf.generated_at",
                new Object[]{DATE_FORMAT.format(meta.generatedAt() == null ? Instant.now() : meta.generatedAt())}, loc);
        doc.add(new Paragraph(generatedAt)
                .setTextAlignment(TextAlignment.RIGHT).setFontSize(8));
        if (meta.periodLabel() != null) {
            doc.add(new Paragraph(meta.periodLabel())
                    .setTextAlignment(TextAlignment.RIGHT).setFontSize(9));
        }
        // Thin navy rule with a short gold accent on the left, as a 2-cell borderless table.
        Table rule = new Table(UnitValue.createPercentArray(new float[]{15, 85})).useAllAvailableWidth();
        rule.addCell(new Cell().setHeight(3f).setBackgroundColor(GOLD)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        rule.addCell(new Cell().setHeight(3f).setBackgroundColor(NAVY)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        rule.setMarginTop(4f).setMarginBottom(8f);
        doc.add(rule);
    }

    /** Bottom-of-page signature: two thin underlined fields (signature, date), no box,
     *  kept together so the block never splits across a page break. */
    public static void renderSignatureBlock(Document doc, MessageSource ms, Locale loc) {
        doc.add(new Paragraph("\n"));
        Table sig = new Table(UnitValue.createPercentArray(new float[]{55, 45})).useAllAvailableWidth();
        sig.setKeepTogether(true);

        Cell signature = new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(
                        com.itextpdf.kernel.colors.ColorConstants.GRAY, 0.5f))
                .setPaddingTop(28f)
                .add(new Paragraph(ms.getMessage("report.pdf.signature", null, loc)).setFontSize(9));

        Cell date = new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(
                        com.itextpdf.kernel.colors.ColorConstants.GRAY, 0.5f))
                .setPaddingTop(28f)
                .setMarginLeft(16f)
                .add(new Paragraph(ms.getMessage("report.pdf.signature.date", null, loc)).setFontSize(9));

        sig.addCell(signature);
        sig.addCell(date);
        doc.add(sig);
    }
}
