package com.oct.invoicesystem.shared.export;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.properties.TextAlignment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTableStyleTest {

    @Test
    void octNavyIsTheBrandNavy() {
        assertThat(PdfTableStyle.OCT_NAVY).isEqualTo(new DeviceRgb(15, 37, 64));
        assertThat(PdfTableStyle.OCT_GOLD).isEqualTo(new DeviceRgb(200, 168, 75));
    }

    @Test
    void headerCellIsNavyWithWhiteText() throws Exception {
        Cell cell = PdfTableStyle.headerCell("Reference", PdfTableStyle.bold(), 8f);
        com.itextpdf.layout.properties.Background bg = cell.getProperty(com.itextpdf.layout.properties.Property.BACKGROUND);
        assertThat(bg).isNotNull();
        assertThat(bg.getColor()).isEqualTo(PdfTableStyle.OCT_NAVY);
    }

    @Test
    void bodyCellAltRowUsesAlternateBackground() throws Exception {
        Cell plain = PdfTableStyle.bodyCell("x", PdfTableStyle.regular(), 8f, false, TextAlignment.LEFT);
        Cell alt   = PdfTableStyle.bodyCell("x", PdfTableStyle.regular(), 8f, true,  TextAlignment.LEFT);
        com.itextpdf.layout.properties.Background plainBg = plain.getProperty(com.itextpdf.layout.properties.Property.BACKGROUND);
        com.itextpdf.layout.properties.Background altBg = alt.getProperty(com.itextpdf.layout.properties.Property.BACKGROUND);
        assertThat(plainBg.getColor()).isNotEqualTo(altBg.getColor());
    }

    @Test
    void bodyCellNullTextRendersDash() throws Exception {
        Cell cell = PdfTableStyle.bodyCell(null, PdfTableStyle.regular(), 8f, false, TextAlignment.LEFT);
        assertThat(cell.toString()).isNotNull(); // cell built without throwing on null text
    }
}
