package com.oct.invoicesystem.domain.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    // Use dedicated app.mail.from — spring.mail.username may be empty when no SMTP auth is required
    @Value("${app.mail.from:noreply@oct.ga}")
    private String fromAddress;

    @Value("${app.mail.from-name:OCT Invoice System}")
    private String fromName;

    /**
     * Send a single HTML email using a Thymeleaf template.
     *
     * @param to           recipient email address
     * @param subject      email subject
     * @param templateName template filename without extension (e.g. "invoice-submitted")
     * @param variables    variables to inject into the template
     */
    @Override
    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            Context ctx = new Context();
            ctx.setVariables(variables);
            String htmlContent = templateEngine.process("email/" + templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent to {} with subject '{}'", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            // Intentionally not re-throwing — a failed email must never roll back a transaction
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Send the same email to multiple recipients.
     *
     * @param emails       list of recipient email addresses
     * @param subject      email subject
     * @param templateName template filename without extension
     * @param variables    variables to inject into the template
     */
    @Override
    public void sendEmailToUsers(List<String> emails, String subject, String templateName, Map<String, Object> variables) {
        emails.forEach(email -> sendEmail(email, subject, templateName, variables));
    }

    @Override
    public void sendEmailWithAttachment(String to, String subject, String bodyText,
                                        byte[] attachment, String attachmentFilename, String attachmentContentType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(bodyText == null ? "" : bodyText, false);
            if (attachment != null && attachment.length > 0) {
                helper.addAttachment(attachmentFilename,
                        new org.springframework.core.io.ByteArrayResource(attachment), attachmentContentType);
            }
            mailSender.send(message);
            log.info("Report email sent to {} with attachment '{}'", to, attachmentFilename);
        } catch (MessagingException e) {
            log.error("Failed to send report email to {}: {}", to, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending report email to {}: {}", to, e.getMessage());
        }
    }
}
