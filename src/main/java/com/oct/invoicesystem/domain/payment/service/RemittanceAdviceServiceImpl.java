package com.oct.invoicesystem.domain.payment.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.oct.invoicesystem.domain.payment.dto.RemittanceAdviceDTO;
import com.oct.invoicesystem.domain.payment.model.Payment;
import com.oct.invoicesystem.domain.payment.model.RemittanceAdvice;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.payment.repository.RemittanceAdviceRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemittanceAdviceServiceImpl implements RemittanceAdviceService {

    private final PaymentRepository paymentRepository;
    private final RemittanceAdviceRepository remittanceAdviceRepository;
    private final UserRepository userRepository;
    private final MinioStorageService minioStorageService;
    private final MessageSource messageSource;

    @Override
    @Transactional
    public RemittanceAdviceDTO generateRemittanceAdvice(UUID paymentId, UUID userId) {
        log.info("Generating remittance advice for payment {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found id: " + paymentId));

        User generatedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found id: " + userId));

        Locale locale = LocaleContextHolder.getLocale();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Title
            String title = messageSource.getMessage("remittance.title", null, "Remittance Advice", locale);
            document.add(new Paragraph(title)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(20)
                    .setBold());

            // Generated at
            String generatedAt = formatter.format(Instant.now());
            document.add(new Paragraph(messageSource.getMessage("report.pdf.generated_at", 
                    new Object[]{generatedAt}, "Generated at: {0}", locale))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontSize(8));

            document.add(new Paragraph("\n"));

            // Payment Summary Table
            Table summary = new Table(UnitValue.createPercentArray(new float[]{30, 70})).useAllAvailableWidth();
            
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    messageSource.getMessage("remittance.invoice.ref", null, "Invoice Reference", locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    payment.getInvoice().getReferenceNumber())));
            
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    messageSource.getMessage("remittance.supplier", null, "Supplier", locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    payment.getInvoice().getSupplierName())));
            
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    messageSource.getMessage("remittance.amount", null, "Amount Paid", locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    payment.getAmountPaid().toString() + " " + payment.getInvoice().getCurrency())));
            
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    messageSource.getMessage("remittance.payment_date", null, "Payment Date", locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    formatter.format(payment.getPaymentDate()))));
            
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    messageSource.getMessage("remittance.payment_method", null, "Payment Method", locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    payment.getPaymentMethod().toString())));
            
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    messageSource.getMessage("remittance.reference", null, "Reference Number", locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(
                    payment.getReference() != null ? payment.getReference() : "-")));
            
            document.add(summary);
            document.close();

            byte[] pdfContent = out.toByteArray();
            
            // Generate MinIO object key
            String timestamp = String.valueOf(System.currentTimeMillis());
            String objectKey = String.format("remittance/%s/%s.pdf", paymentId, timestamp);

            // Upload to MinIO
            minioStorageService.upload(objectKey, pdfContent, "application/pdf");
            log.info("Uploaded remittance advice to MinIO: {}", objectKey);

            // Record in database
            RemittanceAdvice remittanceAdvice = RemittanceAdvice.builder()
                    .payment(payment)
                    .pdfObjectKey(objectKey)
                    .generatedBy(generatedBy)
                    .generatedAt(Instant.now())
                    .build();

            remittanceAdvice = remittanceAdviceRepository.save(remittanceAdvice);
            log.info("Recorded remittance advice {} for payment {}", remittanceAdvice.getId(), paymentId);

            return toDTO(remittanceAdvice);
        } catch (Exception e) {
            log.error("Error generating remittance advice PDF", e);
            throw new RuntimeException("Failed to generate remittance advice: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RemittanceAdviceDTO getByPaymentId(UUID paymentId) {
        RemittanceAdvice advice = remittanceAdviceRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Remittance advice not found for payment " + paymentId));
        return toDTO(advice);
    }

    @Override
    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID paymentId) {
        RemittanceAdvice advice = remittanceAdviceRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Remittance advice not found for payment " + paymentId));
        
        try {
            return minioStorageService.generateDownloadUrl(advice.getPdfObjectKey());
        } catch (Exception e) {
            log.error("Error generating download URL for remittance", e);
            throw new RuntimeException("Failed to generate download URL: " + e.getMessage());
        }
    }

    private RemittanceAdviceDTO toDTO(RemittanceAdvice advice) {
        return new RemittanceAdviceDTO(
                advice.getId(),
                advice.getPayment().getId(),
                advice.getPdfObjectKey(),
                advice.getGeneratedAt(),
                advice.getGeneratedBy() != null ? advice.getGeneratedBy().getId() : null
        );
    }
}
