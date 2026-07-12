package com.oct.invoicesystem.shared.export;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;

/**
 * OCT letterhead for generated PDFs: embeds the company logo (classpath
 * {@code /branding/oct-logo.png}) at the top of a document. The logo is loaded once and
 * cached; a missing resource silently downgrades to a logo-less document so report
 * generation never fails because of branding.
 */
public final class PdfBranding {

    private static final String LOGO_RESOURCE = "/branding/oct-logo.png";
    private static final float LOGO_WIDTH_PT = 150f;

    private static volatile byte[] logoBytes;
    private static volatile boolean lookupDone;

    private PdfBranding() {}

    /** Adds the OCT logo at the current position (top) of the document, if available. */
    public static void addLetterhead(Document document) {
        byte[] logo = logo();
        if (logo == null) {
            return;
        }
        Image img = new Image(ImageDataFactory.create(logo));
        img.setWidth(LOGO_WIDTH_PT);
        document.add(img);
    }

    private static byte[] logo() {
        if (!lookupDone) {
            synchronized (PdfBranding.class) {
                if (!lookupDone) {
                    try (var in = PdfBranding.class.getResourceAsStream(LOGO_RESOURCE)) {
                        logoBytes = in == null ? null : in.readAllBytes();
                    } catch (Exception e) {
                        logoBytes = null;
                    }
                    lookupDone = true;
                }
            }
        }
        return logoBytes;
    }
}
