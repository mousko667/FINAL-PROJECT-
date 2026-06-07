package com.oct.invoicesystem.domain.ocr.service;

import com.oct.invoicesystem.domain.ocr.dto.OcrExtractionResult;
import com.oct.invoicesystem.domain.ocr.dto.OcrExtractionResult.OcrLineItem;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts invoice fields from uploaded files (PDF, JPEG, PNG, TIFF) using:
 * - PDFBox text-layer extraction for digital PDFs
 * - Tess4J (Tesseract OCR) for scanned PDFs and images
 *
 * Tika is used for MIME type detection only — InvoiceDocumentService already validates
 * the MIME type before this service is called, but we re-detect here to route correctly.
 */
@Slf4j
@Service
public class OcrService {

    private final Tika tika = new Tika();

    @Value("${ocr.tessdata-path:tessdata}")
    private String tessdataPath;

    @Value("${ocr.language:fra+eng}")
    private String ocrLanguage;

    /**
     * Extracts structured invoice fields from the given file bytes.
     *
     * @param fileBytes raw bytes of the uploaded invoice file
     * @param originalFilename original filename (used for logging only)
     * @return extracted fields ready for supplier confirmation
     */
    public OcrExtractionResult extract(byte[] fileBytes, String originalFilename) {
        String mimeType = tika.detect(fileBytes);
        log.info("OCR extraction started for file='{}' mimeType='{}'", originalFilename, mimeType);

        String rawText;
        boolean digitalPdf = false;

        try {
            if ("application/pdf".equals(mimeType)) {
                String pdfLayerText = extractPdfTextLayer(fileBytes);
                if (pdfLayerText != null && pdfLayerText.trim().length() > 50) {
                    rawText = pdfLayerText;
                    digitalPdf = true;
                    log.debug("PDF text layer used for '{}' ({} chars)", originalFilename, rawText.length());
                } else {
                    rawText = ocrPdf(fileBytes, originalFilename);
                    log.debug("Tess4J OCR used for scanned PDF '{}'", originalFilename);
                }
            } else {
                rawText = ocrImage(fileBytes, originalFilename);
                log.debug("Tess4J OCR used for image '{}' mimeType='{}'", originalFilename, mimeType);
            }
        } catch (Exception e) {
            log.warn("OCR extraction failed for '{}': {}", originalFilename, e.getMessage());
            rawText = "";
        }

        return parseFields(rawText, digitalPdf);
    }

    // ── PDF text-layer extraction via PDFBox ─────────────────────────────────

    private String extractPdfTextLayer(byte[] fileBytes) {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            log.debug("PDFBox text extraction failed, falling back to OCR: {}", e.getMessage());
            return null;
        }
    }

    // ── Tess4J OCR on scanned PDF pages ──────────────────────────────────────

    private String ocrPdf(byte[] fileBytes, String filename) throws IOException, TesseractException {
        Tesseract tesseract = buildTesseract();
        StringBuilder sb = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 300, ImageType.RGB);
                File tmpFile = writeToTempFile(image, filename + "_page" + page);
                try {
                    sb.append(tesseract.doOCR(tmpFile));
                } finally {
                    tmpFile.delete();
                }
            }
        }
        return sb.toString();
    }

    // ── Tess4J OCR on image files ─────────────────────────────────────────────

    private String ocrImage(byte[] fileBytes, String filename) throws IOException, TesseractException {
        Tesseract tesseract = buildTesseract();
        File tmpFile = Files.createTempFile("oct_ocr_", "_" + filename).toFile();
        try {
            Files.write(tmpFile.toPath(), fileBytes);
            return tesseract.doOCR(tmpFile);
        } finally {
            tmpFile.delete();
        }
    }

    private Tesseract buildTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(ocrLanguage);
        tesseract.setPageSegMode(1);
        tesseract.setOcrEngineMode(1);
        return tesseract;
    }

    private File writeToTempFile(BufferedImage image, String label) throws IOException {
        File tmp = Files.createTempFile("oct_ocr_", "_" + label + ".png").toFile();
        ImageIO.write(image, "png", tmp);
        return tmp;
    }

    // ── Field parsing ─────────────────────────────────────────────────────────

    /**
     * Parses raw OCR text into structured invoice fields using regex heuristics.
     * The supplier confirms and corrects these before final submission.
     */
    OcrExtractionResult parseFields(String rawText, boolean digitalPdf) {
        return OcrExtractionResult.builder()
                .invoiceNumber(extractInvoiceNumber(rawText))
                .invoiceDate(extractDate(rawText))
                .totalAmount(extractAmount(rawText))
                .supplierId(extractSupplierId(rawText))
                .poReference(extractPoReference(rawText))
                .lineItems(extractLineItems(rawText))
                .rawText(rawText)
                .digitalPdf(digitalPdf)
                .build();
    }

    private String extractInvoiceNumber(String text) {
        // Matches: "Invoice No: FAC-2026-00041", "Facture N°: xxx", "Invoice #: xxx"
        Pattern p = Pattern.compile(
                "(?i)(?:invoice\\s*(?:no|num|number|#)|facture\\s*n[°o]?)\\s*[:\\-]?\\s*([A-Z0-9\\-/]+)",
                Pattern.UNICODE_CASE);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractDate(String text) {
        // Matches common date formats: dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd
        Pattern p = Pattern.compile(
                "(?i)(?:date|dated?|issued?)\\s*[:\\-]?\\s*(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}|\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2})");
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1).trim();
        // Fallback: any date-like pattern
        Pattern fallback = Pattern.compile("(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{4})");
        Matcher fm = fallback.matcher(text);
        return fm.find() ? fm.group(1).trim() : null;
    }

    private BigDecimal extractAmount(String text) {
        // Matches: "Total: 450 000,00" or "Total: 450,000.00" or "TOTAL XAF 450000"
        Pattern p = Pattern.compile(
                "(?i)(?:total\\s*(?:amount|ttc|ht|xaf)?|montant\\s*(?:total)?|amount\\s*due)\\s*[:\\-]?\\s*(?:xaf|fcfa|eur|usd)?\\s*([\\d\\s.,]+)",
                Pattern.UNICODE_CASE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String raw = m.group(1).trim().replaceAll("\\s", "").replace(",", ".");
            // Handle European format: 450.000,00 → 450000.00
            if (raw.matches(".*\\.\\d{3},\\d{2}")) {
                raw = raw.replace(".", "").replace(",", ".");
            }
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException e) {
                log.debug("Could not parse amount from '{}'", m.group(1));
            }
        }
        return null;
    }

    private String extractSupplierId(String text) {
        // Matches tax IDs / RCCM / NIF patterns common in Gabon
        Pattern p = Pattern.compile(
                "(?i)(?:nif|tax\\s*id|n°\\s*contribuable|rccm|siret)\\s*[:\\-]?\\s*([A-Z0-9\\-/]+)");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractPoReference(String text) {
        Pattern p = Pattern.compile(
                "(?i)(?:p[./]?o\\.?\\s*(?:number|no|ref|reference)?|bon\\s*de\\s*commande\\s*n[°o]?)\\s*[:\\-]?\\s*([A-Z0-9\\-/]+)");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private List<OcrLineItem> extractLineItems(String text) {
        List<OcrLineItem> items = new ArrayList<>();
        // Match lines like: "  2  Écran 27 pouces  175 000"
        Pattern p = Pattern.compile(
                "^\\s*(\\d+)\\s+(.+?)\\s+(\\d[\\d\\s.,]*)\\s+(\\d[\\d\\s.,]*)\\s*$",
                Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        while (m.find() && items.size() < 20) {
            items.add(OcrLineItem.builder()
                    .description(m.group(2).trim())
                    .quantity(m.group(3).trim())
                    .unitPrice(m.group(4).trim())
                    .build());
        }
        return items;
    }
}
