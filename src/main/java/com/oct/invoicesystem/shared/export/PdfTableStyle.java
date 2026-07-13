package com.oct.invoicesystem.shared.export;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

import java.io.IOException;

/**
 * Shared visual identity for exported PDF tables (OCT navy/gold, Helvetica, navy header cells,
 * zebra body cells). Extracted from InvoicePdfService so list exports and the single-invoice
 * document share one look. Stateless: only cell/font factories, no data, no page layout.
 */
public final class PdfTableStyle {

    public static final DeviceRgb OCT_NAVY   = new DeviceRgb(15, 37, 64);
    public static final DeviceRgb OCT_GOLD   = new DeviceRgb(200, 168, 75);
    public static final DeviceRgb ROW_ALT    = new DeviceRgb(243, 244, 246);
    public static final DeviceRgb LIGHT_GRAY = new DeviceRgb(248, 249, 250);
    private static final DeviceRgb WHITE      = new DeviceRgb(255, 255, 255);

    private PdfTableStyle() {}

    public static PdfFont bold() throws IOException {
        return PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
    }

    public static PdfFont regular() throws IOException {
        return PdfFontFactory.createFont(StandardFonts.HELVETICA);
    }

    /** Navy background, white bold text — table header cell. */
    public static Cell headerCell(String text, PdfFont bold, float size) {
        return new Cell()
                .add(new Paragraph(text == null ? "" : text)
                        .setFont(bold).setFontSize(size).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(OCT_NAVY)
                .setBorder(new SolidBorder(OCT_NAVY, 0.5f))
                .setPadding(5);
    }

    /** Zebra body cell: white or ROW_ALT background, thin gray border, given alignment. */
    public static Cell bodyCell(String text, PdfFont font, float size, boolean alt, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text == null || text.isBlank() ? "—" : text)
                        .setFont(font).setFontSize(size).setTextAlignment(align))
                .setBackgroundColor(alt ? ROW_ALT : WHITE)
                .setBorder(new SolidBorder(LIGHT_GRAY, 0.5f))
                .setPadding(4)
                .setTextAlignment(align);
    }
}
