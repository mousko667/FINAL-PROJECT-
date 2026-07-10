package com.oct.invoicesystem.domain.report.scheduler;

import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.report.model.ReportDefinition;
import com.oct.invoicesystem.domain.report.repository.ReportDefinitionRepository;
import com.oct.invoicesystem.domain.report.service.ReportBuilderService;
import com.oct.invoicesystem.shared.export.TabularExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * M11 scheduled report distribution. Runs each active definition due for its frequency and emails
 * the rendered file to its recipients. Three cron entries map to DAILY / WEEKLY / MONTHLY.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledReportJob {

    private final ReportDefinitionRepository repository;
    private final ReportBuilderService reportBuilderService;
    private final EmailService emailService;

    @Scheduled(cron = "${app.reports.cron.daily:0 0 6 * * *}")
    public void runDaily() { distribute("DAILY"); }

    @Scheduled(cron = "${app.reports.cron.weekly:0 0 6 * * MON}")
    public void runWeekly() { distribute("WEEKLY"); }

    @Scheduled(cron = "${app.reports.cron.monthly:0 0 6 1 * *}")
    public void runMonthly() { distribute("MONTHLY"); }

    @Transactional
    public void distribute(String frequency) {
        List<ReportDefinition> due = repository.findByActiveTrueAndFrequency(frequency);
        if (due.isEmpty()) return;
        log.info("Scheduled report distribution: {} {} report(s) due.", due.size(), frequency);
        for (ReportDefinition def : due) {
            try {
                byte[] bytes = reportBuilderService.render(def, null);
                TabularExportService.Format fmt = TabularExportService.Format.from(def.getFormat());
                String filename = sanitize(def.getName()) + "." + fmt.extension;
                for (String email : recipients(def)) {
                    emailService.sendEmailWithAttachment(email,
                            "Rapport planifié: " + def.getName(),
                            "Veuillez trouver ci-joint le rapport planifié « " + def.getName() + " ».",
                            bytes, filename, fmt.mediaType);
                }
                def.setLastRunAt(Instant.now());
                repository.save(def);
            } catch (Exception e) {
                log.error("Failed to distribute report '{}': {}", def.getName(), e.getMessage());
            }
        }
    }

    private List<String> recipients(ReportDefinition def) {
        if (def.getRecipients() == null || def.getRecipients().isBlank()) return List.of();
        return Arrays.stream(def.getRecipients().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String sanitize(String name) {
        return name == null ? "report" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
