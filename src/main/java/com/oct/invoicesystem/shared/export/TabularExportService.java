package com.oct.invoicesystem.shared.export;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One place that turns a header row + string data rows into CSV, XLSX or PDF bytes.
 * Shared by every "export" endpoint so all modules offer the same formats consistently
 * (instead of CSV-here, Excel-there). CSV escaping is RFC-4180 with a leading-quote guard
 * against spreadsheet formula injection.
 */
@Service
public class TabularExportService {

    public enum Format {
        CSV("text/csv", "csv"),
        EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
        PDF("application/pdf", "pdf");

        public final String mediaType;
        public final String extension;

        Format(String mediaType, String extension) {
            this.mediaType = mediaType;
            this.extension = extension;
        }

        public static Format from(String raw) {
            if (raw == null) return CSV;
            return switch (raw.trim().toLowerCase()) {
                case "excel", "xlsx" -> EXCEL;
                case "pdf" -> PDF;
                default -> CSV;
            };
        }
    }

    public byte[] export(Format format, String title, List<String> headers, List<List<String>> rows) {
        return export(format, title, headers, rows, null, null);
    }

    /** Overload used when a caller has no MessageSource but wants no metadata (meta must be null). */
    public byte[] export(Format format, String title, List<String> headers, List<List<String>> rows, ReportMetadata meta) {
        return export(format, title, headers, rows, meta, null);
    }

    /**
     * Full export. For PDF, when {@code meta} is non-null a metadata header (generator, date,
     * optional period) and a signature box are rendered using {@code messageSource}; CSV/EXCEL
     * ignore {@code meta}. When {@code meta} is null the output is byte-for-byte the legacy layout.
     */
    public byte[] export(Format format, String title, List<String> headers, List<List<String>> rows,
                         ReportMetadata meta, org.springframework.context.MessageSource messageSource) {
        return switch (format) {
            case CSV -> toCsv(headers, rows);
            case EXCEL -> toExcel(title, headers, rows);
            case PDF -> toPdf(title, headers, rows, meta, messageSource);
        };
    }

    // ── CSV ──────────────────────────────────────────────────────────────────
    private byte[] toCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(joinCsv(headers)).append("\r\n");
        for (List<String> row : rows) {
            sb.append(joinCsv(row)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String joinCsv(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(values.get(i)));
        }
        return sb.toString();
    }

    /**
     * Spreadsheet formula-injection guard, shared by CSV and Excel sinks: a cell starting with
     * = + - @ (or a leading tab/CR that some apps treat as a formula lead) gets a single-quote
     * prefix so it is rendered as text, not executed.
     */
    static String neutralizeFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        char c = value.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + value;
        }
        return value;
    }

    private String escapeCsv(String value) {
        String v = neutralizeFormula(value);
        boolean mustQuote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (v.contains("\"")) {
            v = v.replace("\"", "\"\"");
        }
        return mustQuote ? "\"" + v + "\"" : v;
    }

    // ── Excel ────────────────────────────────────────────────────────────────
    private byte[] toExcel(String title, List<String> headers, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(title));

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (List<String> row : rows) {
                Row r = sheet.createRow(rowIdx++);
                for (int i = 0; i < row.size(); i++) {
                    // Same formula-injection guard as CSV — Excel evaluates =,+,-,@ leads too.
                    r.createCell(i).setCellValue(neutralizeFormula(row.get(i)));
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ExportException("Failed to build Excel export", e);
        }
    }

    private String safeSheetName(String title) {
        String name = title == null || title.isBlank() ? "Export" : title;
        // Excel sheet names: max 31 chars, no : \ / ? * [ ]
        name = name.replaceAll("[:\\\\/?*\\[\\]]", " ");
        return name.length() > 31 ? name.substring(0, 31) : name;
    }

    /**
     * Weighted column widths (as percentages summing to 100) so wide text columns (email,
     * description, supplier name) get more room than narrow ones (amount, date, code). Keeps an
     * 11-column table inside an A4 portrait page instead of overflowing off the right margin.
     */
    static float[] columnWeights(java.util.List<String> headers) {
        String[] wideKeys = {"email", "description", "libell", "label", "supplier", "fournisseur", "name", "nom"};
        float[] weights = new float[headers.size()];
        float total = 0f;
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i) == null ? "" : headers.get(i).toLowerCase();
            boolean wide = false;
            for (String k : wideKeys) {
                if (h.contains(k)) { wide = true; break; }
            }
            weights[i] = wide ? 3f : 1f;
            total += weights[i];
        }
        if (total == 0f) total = headers.size();
        for (int i = 0; i < weights.length; i++) {
            weights[i] = weights[i] / total * 100f;
        }
        return weights;
    }

    // ── PDF ──────────────────────────────────────────────────────────────────
    private byte[] toPdf(String title, List<String> headers, List<List<String>> rows,
                         ReportMetadata meta, org.springframework.context.MessageSource messageSource) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfBranding.addLetterhead(document);
            document.add(new Paragraph(title == null ? "Export" : title).setBold().setFontSize(14));

            if (meta != null && messageSource != null) {
                PdfMetadata.renderHeader(document, meta, messageSource,
                        org.springframework.context.i18n.LocaleContextHolder.getLocale());
            }

            float[] widths = new float[headers.size()];
            for (int i = 0; i < widths.length; i++) widths[i] = 1f;
            Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();

            for (String h : headers) {
                table.addHeaderCell(new Cell().add(new Paragraph(h == null ? "" : h).setBold()));
            }
            for (List<String> row : rows) {
                for (String v : row) {
                    table.addCell(new Cell().add(new Paragraph(v == null ? "" : v)));
                }
            }

            document.add(table);

            if (meta != null && messageSource != null) {
                PdfMetadata.renderSignatureBlock(document, messageSource,
                        org.springframework.context.i18n.LocaleContextHolder.getLocale());
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ExportException("Failed to build PDF export", e);
        }
    }

    public static class ExportException extends RuntimeException {
        public ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
