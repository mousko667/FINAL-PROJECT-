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

    /** Header block under the title: 3-column layout with logo, title, and period. */
    public static void renderHeader(Document doc, String title, ReportMetadata meta, MessageSource ms, Locale loc) {
        // Top: Logo
        com.itextpdf.layout.element.Image logo = PdfBranding.getLogoImage();
        if (logo != null) {
            logo.setMarginBottom(0f); // Reduced spacing between logo and report content
            doc.add(logo);
        }

        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{20, 45, 35})).useAllAvailableWidth();
        
        // Left: Empty cell to balance the right cell
        headerTable.addCell(new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));

        // Center: Title
        Cell centerCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER);
        com.itextpdf.kernel.font.PdfFont boldFont;
        try {
            boldFont = PdfTableStyle.bold();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load bold font", e);
        }
        centerCell.add(new Paragraph(title == null ? "Export" : title)
                .setFont(boldFont).setFontSize(14).setFontColor(PdfTableStyle.OCT_NAVY));
        
        if (meta != null && meta.filtersLabel() != null) {
            String filtersLine = ms.getMessage("report.pdf.filters",
                    new Object[]{meta.filtersLabel()}, meta.filtersLabel(), loc);
            centerCell.add(new Paragraph(filtersLine)
                    .setFontSize(9)
                    .setItalic());
        }
        headerTable.addCell(centerCell);
        // Right: Report Period and Generated At
        Cell rightCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        if (meta != null && meta.periodLabel() != null) {
            String label = meta.periodLabel().replace("\\n", "\n");
            String[] parts = label.split("\n", 2);
            if (parts.length == 2) {
                rightCell.add(new Paragraph(parts[0]).setFont(boldFont).setFontColor(PdfTableStyle.OCT_NAVY).setFontSize(9));
                rightCell.add(new Paragraph(parts[1]).setFontColor(PdfTableStyle.OCT_NAVY).setFontSize(9));
            } else {
                rightCell.add(new Paragraph(label).setFont(boldFont).setFontColor(PdfTableStyle.OCT_NAVY).setFontSize(9));
            }
        }
        
        if (meta != null) {
            String generatedAt = ms.getMessage("report.pdf.generated_at",
                    new Object[]{DATE_FORMAT.format(meta.generatedAt() == null ? Instant.now() : meta.generatedAt())}, loc);
            rightCell.add(new Paragraph(generatedAt).setFontSize(8).setItalic().setMarginTop(4f));
        }
        headerTable.addCell(rightCell);

        doc.add(headerTable);

        // Blue separator line
        doc.add(new com.itextpdf.layout.element.LineSeparator(
                        new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1.5f))
                .setStrokeColor(PdfTableStyle.OCT_NAVY).setMarginTop(6).setMarginBottom(10));
    }

    /** Bottom-of-page layout with 'Prepared by', Name, Role, and signature line. */
    public static void renderFooter(Document doc, ReportMetadata meta, MessageSource ms, Locale loc) {
        doc.add(new Paragraph("\n"));
        
        Table footerTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
        
        Cell leftCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        
        String preparedByLabel = ms.getMessage("report.pdf.prepared_by", null, "Generated by", loc);
        leftCell.add(new Paragraph(preparedByLabel).setFontColor(PdfTableStyle.OCT_NAVY).setFontSize(8));
        
        if (meta != null) {
            String name = meta.generatorName() != null ? meta.generatorName() : "";
            String role = meta.generatorRole() != null ? meta.generatorRole() : "";
            leftCell.add(new Paragraph(name).setFontSize(9));
            leftCell.add(new Paragraph(role).setFontSize(9));
        }
        
        leftCell.add(new Paragraph("\n\n")); // Additional space before signature line
        leftCell.add(new Paragraph("__________________________").setFontSize(10));
        leftCell.add(new Paragraph("Signature").setFontSize(8));
        leftCell.add(new Paragraph("\n"));
        
        leftCell.add(new Paragraph("\n"));
        
        footerTable.addCell(leftCell);
        
        // Empty right cell
        Cell rightCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        footerTable.addCell(rightCell);
        
        doc.add(footerTable);
    }
}
