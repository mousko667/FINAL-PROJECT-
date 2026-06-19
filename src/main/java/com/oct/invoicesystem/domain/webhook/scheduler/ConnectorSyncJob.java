package com.oct.invoicesystem.domain.webhook.scheduler;

import com.oct.invoicesystem.domain.webhook.model.IntegrationConnector;
import com.oct.invoicesystem.domain.webhook.repository.IntegrationConnectorRepository;
import com.oct.invoicesystem.domain.webhook.service.IntegrationConnectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled connector synchronisation (B6, M12 #10). Every minute, scans enabled connectors that
 * have a configured {@code syncIntervalMinutes} and triggers a sync for those whose interval has
 * elapsed since their last sync. A connector that has never been synced is due immediately. A failing
 * sync is logged and never aborts the loop for the remaining connectors.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectorSyncJob {

    private final IntegrationConnectorRepository repository;
    private final IntegrationConnectorService service;

    @Scheduled(fixedDelayString = "${app.connector-sync.poll-ms:60000}")
    public void runScheduledSyncs() {
        List<IntegrationConnector> candidates =
                repository.findBySyncIntervalMinutesIsNotNullAndEnabledTrue();
        Instant now = Instant.now();

        for (IntegrationConnector c : candidates) {
            if (!isDue(c, now)) continue;
            try {
                service.syncNow(c.getId());
                log.info("Scheduled sync triggered for connector {} ({})", c.getName(), c.getId());
            } catch (Exception e) {
                log.error("Scheduled sync failed for connector {} ({}): {}",
                        c.getName(), c.getId(), e.getMessage());
            }
        }
    }

    private boolean isDue(IntegrationConnector c, Instant now) {
        if (c.getLastSyncAt() == null) return true;
        long elapsed = ChronoUnit.MINUTES.between(c.getLastSyncAt(), now);
        return elapsed >= c.getSyncIntervalMinutes();
    }
}
