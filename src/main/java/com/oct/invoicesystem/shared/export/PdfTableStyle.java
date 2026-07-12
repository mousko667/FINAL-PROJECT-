package com.oct.invoicesystem.shared.export;

/**
 * Shared PDF table styling (navy header, zebra striping, thin hairline separators, no vertical
 * borders) used by every hand-built iText table across report/export PDFs so they render
 * consistently instead of duplicating the same color constants and cell setup in each caller.
 */
public final class PdfTableStyle {

    public static final com.itextpdf.kernel.colors.Color NAVY =
            new com.itextpdf.kernel.colors.DeviceRgb(15, 37, 64);     // #0F2540
    public static final com.itextpdf.kernel.colors.Color ZEBRA =
            new com.itextpdf.kernel.colors.DeviceRgb(251, 250, 247);  // #FBFAF7
    public static final com.itextpdf.kernel.colors.Color HAIRLINE =
            new com.itextpdf.kernel.colors.DeviceRgb(218, 211, 196);  // #DAD3C4

    private PdfTableStyle() {
    }

    /** En-tête : fond navy, texte blanc gras, aucune bordure, padding 5. Le Paragraph fourni est ajouté tel quel. */
    public static com.itextpdf.layout.element.Cell headerCell(com.itextpdf.layout.element.Paragraph content) {
        return new com.itextpdf.layout.element.Cell()
                .add(content.setBold())
                .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
                .setBackgroundColor(NAVY)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(5f);
    }

    /** Cellule data : aucune bordure sauf un filet inférieur fin hairline (0.3), padding 4, zébrure optionnelle. */
    public static com.itextpdf.layout.element.Cell dataCell(com.itextpdf.layout.element.Paragraph content, boolean zebra) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                .add(content)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(HAIRLINE, 0.3f))
                .setPadding(4f);
        if (zebra) {
            cell.setBackgroundColor(ZEBRA);
        }
        return cell;
    }
}
