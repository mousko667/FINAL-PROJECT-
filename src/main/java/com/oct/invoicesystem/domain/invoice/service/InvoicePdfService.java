package com.oct.invoicesystem.domain.invoice.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Generates a compliance-grade PDF export of an invoice using iText 8.
 * Includes: header, invoice metadata, line items, approval chain history, and footer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private static final DeviceRgb OCT_NAVY   = new DeviceRgb(15, 37, 64);
    private static final DeviceRgb OCT_GOLD   = new DeviceRgb(200, 168, 75);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(248, 249, 250);
    private static final DeviceRgb ROW_ALT    = new DeviceRgb(243, 244, 246);
    private static final DeviceRgb GREEN_BG   = new DeviceRgb(240, 253, 244);
    private static final DeviceRgb RED_BG     = new DeviceRgb(254, 242, 242);
    private static final DeviceRgb AMBER_BG   = new DeviceRgb(255, 251, 235);

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Libreville"));

    private final InvoiceRepository invoiceRepository;
    private final ApprovalStepRepository approvalStepRepository;

    /**
     * Generates a PDF byte array for the given invoice.
     * Access control is enforced at the controller level.
     */
    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        List<ApprovalStep> steps = approvalStepRepository
                .findByInvoiceIdOrderByStepOrderAsc(invoiceId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 36, 36, 36);

            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            buildHeader(doc, bold, regular);
            buildInvoiceMeta(doc, invoice, bold, regular);
            buildLineItems(doc, invoice, bold, regular);
            buildApprovalChain(doc, steps, bold, regular);
            buildFooter(doc, invoice, regular);

            doc.close();
            log.info("PDF generated for invoice {} ({} bytes)", invoice.getReferenceNumber(), baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate PDF for invoice {}: {}", invoiceId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private void buildHeader(Document doc, PdfFont bold, PdfFont regular) throws Exception {
        Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .setWidth(UnitValue.createPercentValue(100));

        Cell left = new Cell()
                .add(new Paragraph("OCT").setFont(bold).setFontSize(20).setFontColor(OCT_NAVY))
                .add(new Paragraph("Owendo Container Terminal").setFont(regular).setFontSize(9).setFontColor(ColorConstants.GRAY))
                .add(new Paragraph("Système de Gestion des Factures Fournisseurs").setFont(regular).setFontSize(8).setFontColor(ColorConstants.GRAY))
                .setBorder(null).setPadding(0);

        Cell right = new Cell()
                .add(new Paragraph("BON À PAYER").setFont(bold).setFontSize(11).setFontColor(OCT_NAVY)
                        .setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Document officiel — usage interne").setFont(regular).setFontSize(8).setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Généré le " + DATETIME_FMT.format(Instant.now()))
                        .setFont(regular).setFontSize(8).setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(null).setPadding(0);

        header.addCell(left).addCell(right);
        doc.add(header);
        doc.add(new LineSeparator(new SolidLine(1.5f)).setStrokeColor(OCT_GOLD).setMarginTop(8).setMarginBottom(12));
    }

    private void buildInvoiceMeta(Document doc, Invoice invoice, PdfFont bold, PdfFont regular) throws Exception {
        doc.add(new Paragraph("DÉTAILS DE LA FACTURE").setFont(bold).setFontSize(10).setFontColor(OCT_NAVY).setMarginBottom(6));

        Table meta = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(16);

        addMetaCell(meta, "Référence",    invoice.getReferenceNumber(),                                bold, regular);
        addMetaCell(meta, "Statut",       invoice.getStatus().name(),                                  bold, regular);
        addMetaCell(meta, "Fournisseur",  invoice.getSupplierName(),                                   bold, regular);
        addMetaCell(meta, "Département",  invoice.getDepartment() != null ? invoice.getDepartment().getCode() : "—", bold, regular);
        addMetaCell(meta, "Montant",      formatAmount(invoice.getAmount(), invoice.getCurrency()),    bold, regular);
        addMetaCell(meta, "Date émission",invoice.getIssueDate() != null ? invoice.getIssueDate().toString() : "—", bold, regular);
        addMetaCell(meta, "Date échéance",invoice.getDueDate()   != null ? invoice.getDueDate().toString()   : "—", bold, regular);
        addMetaCell(meta, "Email",        invoice.getSupplierEmail() != null ? invoice.getSupplierEmail() : "—", bold, regular);

        doc.add(meta);

        if (invoice.getDescription() != null && !invoice.getDescription().isBlank()) {
            doc.add(new Paragraph("Description : " + invoice.getDescription())
                    .setFont(regular).setFontSize(9).setFontColor(ColorConstants.GRAY).setMarginBottom(12));
        }
    }

    private void buildLineItems(Document doc, Invoice invoice, PdfFont bold, PdfFont regular) throws Exception {
        List<InvoiceItem> items = invoice.getItems();
        if (items == null || items.isEmpty()) return;

        doc.add(new Paragraph("LIGNES DE FACTURE").setFont(bold).setFontSize(10).setFontColor(OCT_NAVY).setMarginBottom(6));

        Table table = new Table(UnitValue.createPercentArray(new float[]{45, 15, 20, 20}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(16);

        for (String h : new String[]{"Description", "Qté", "Prix unitaire", "Total"}) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(OCT_NAVY).setBorder(new SolidBorder(OCT_NAVY, 0.5f)).setPadding(6));
        }

        boolean alt = false;
        for (InvoiceItem item : items) {
            DeviceRgb bg = alt ? ROW_ALT : new DeviceRgb(255, 255, 255);
            BigDecimal lineTotal = item.getTotalPrice() != null ? item.getTotalPrice()
                    : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity().longValue()));

            table.addCell(styledCell(item.getDescription() != null ? item.getDescription() : "—", regular, 9, bg));
            table.addCell(styledCell(String.valueOf(item.getQuantity()), regular, 9, bg).setTextAlignment(TextAlignment.CENTER));
            table.addCell(styledCell(formatAmount(item.getUnitPrice(), invoice.getCurrency()), regular, 9, bg).setTextAlignment(TextAlignment.RIGHT));
            table.addCell(styledCell(formatAmount(lineTotal, invoice.getCurrency()), bold, 9, bg).setTextAlignment(TextAlignment.RIGHT));
            alt = !alt;
        }

        // Total row
        table.addCell(new Cell(1, 3)
                .add(new Paragraph("TOTAL").setFont(bold).setFontSize(10).setFontColor(OCT_NAVY)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(LIGHT_GRAY).setBorder(new SolidBorder(LIGHT_GRAY, 0.5f))
                .setPaddingRight(8).setPaddingTop(6).setPaddingBottom(6));
        table.addCell(new Cell()
                .add(new Paragraph(formatAmount(invoice.getAmount(), invoice.getCurrency()))
                        .setFont(bold).setFontSize(10).setFontColor(OCT_NAVY).setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(LIGHT_GRAY).setBorder(new SolidBorder(LIGHT_GRAY, 0.5f))
                .setPaddingTop(6).setPaddingBottom(6));

        doc.add(table);
    }

    private void buildApprovalChain(Document doc, List<ApprovalStep> steps, PdfFont bold, PdfFont regular) throws Exception {
        doc.add(new Paragraph("CHAÎNE D'APPROBATION").setFont(bold).setFontSize(10).setFontColor(OCT_NAVY).setMarginBottom(6));

        if (steps.isEmpty()) {
            doc.add(new Paragraph("Aucune étape d'approbation enregistrée.").setFont(regular).setFontSize(9)
                    .setFontColor(ColorConstants.GRAY).setMarginBottom(16));
            return;
        }

        Table table = new Table(UnitValue.createPercentArray(new float[]{8, 27, 15, 15, 20, 15}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(16);

        for (String h : new String[]{"#", "Étape", "Département", "Statut", "Approbateur", "Date"}) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(8).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(OCT_NAVY).setBorder(new SolidBorder(OCT_NAVY, 0.5f)).setPadding(5));
        }

        for (ApprovalStep step : steps) {
            DeviceRgb bg = step.getStatus() == ApprovalStepStatus.APPROVED ? GREEN_BG
                    : step.getStatus() == ApprovalStepStatus.REJECTED ? RED_BG
                    : AMBER_BG;

            table.addCell(styledCell(String.valueOf(step.getStepOrder()), regular, 8, bg).setTextAlignment(TextAlignment.CENTER));
            table.addCell(styledCell(step.getStepNameFr() != null ? step.getStepNameFr() : step.getStepNameEn(), regular, 8, bg));
            table.addCell(styledCell(step.getDepartmentCode(), regular, 8, bg).setTextAlignment(TextAlignment.CENTER));
            table.addCell(styledCell(step.getStatus().name(), bold, 8, bg).setTextAlignment(TextAlignment.CENTER));
            table.addCell(styledCell(
                    step.getApprover() != null
                            ? step.getApprover().getFirstName() + " " + step.getApprover().getLastName()
                            : "—",
                    regular, 8, bg));
            table.addCell(styledCell(
                    step.getActionAt() != null ? DATETIME_FMT.format(step.getActionAt()) : "—",
                    regular, 8, bg));

            if (step.getRejectionReason() != null && !step.getRejectionReason().isBlank()) {
                DeviceRgb redText = new DeviceRgb(185, 28, 28);
                DeviceRgb redBorder = new DeviceRgb(254, 202, 202);
                table.addCell(new Cell(1, 6)
                        .add(new Paragraph("Motif du rejet : " + step.getRejectionReason())
                                .setFont(regular).setFontSize(8).setFontColor(redText))
                        .setBackgroundColor(RED_BG)
                        .setBorder(new SolidBorder(redBorder, 0.5f)).setPadding(4));
            }
        }

        doc.add(table);
    }

    private void buildFooter(Document doc, Invoice invoice, PdfFont regular) throws Exception {
        doc.add(new LineSeparator(new SolidLine(0.5f)).setStrokeColor(ColorConstants.GRAY).setMarginTop(8).setMarginBottom(8));
        doc.add(new Paragraph(
                "Document généré automatiquement par le SGF-OCT. " +
                "Réf : " + invoice.getReferenceNumber() + " · " +
                "Date : " + DATETIME_FMT.format(Instant.now()) + " · " +
                "Ce document est à usage interne exclusivement.")
                .setFont(regular).setFontSize(7).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
    }

    private void addMetaCell(Table table, String label, String value, PdfFont bold, PdfFont regular) {
        Cell cell = new Cell()
                .add(new Paragraph(label).setFont(bold).setFontSize(7).setFontColor(ColorConstants.GRAY))
                .add(new Paragraph(value != null ? value : "—").setFont(regular).setFontSize(9))
                .setBackgroundColor(LIGHT_GRAY).setBorder(new SolidBorder(LIGHT_GRAY, 0.5f)).setPadding(6);
        table.addCell(cell);
    }

    private Cell styledCell(String text, PdfFont font, float size, DeviceRgb bg) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "—").setFont(font).setFontSize(size))
                .setBackgroundColor(bg).setBorder(new SolidBorder(LIGHT_GRAY, 0.5f)).setPadding(5);
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) return "0 " + (currency != null ? currency : "XAF");
        return String.format("%,.0f %s", amount, currency != null ? currency : "XAF");
    }
}
