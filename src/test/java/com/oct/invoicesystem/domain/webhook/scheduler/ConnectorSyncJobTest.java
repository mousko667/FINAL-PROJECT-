package com.oct.invoicesystem.domain.webhook.scheduler;

import com.oct.invoicesystem.domain.webhook.model.IntegrationConnector;
import com.oct.invoicesystem.domain.webhook.repository.IntegrationConnectorRepository;
import com.oct.invoicesystem.domain.webhook.service.IntegrationConnectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorSyncJobTest {

    @Mock private IntegrationConnectorRepository repository;
    @Mock private IntegrationConnectorService service;
    @InjectMocks private ConnectorSyncJob job;

    /** A connector whose interval has elapsed (never synced) must be synced. */
    @Test
    void syncsConnectorWhoseIntervalElapsed() {
        UUID id = UUID.randomUUID();
        IntegrationConnector due = IntegrationConnector.builder()
                .id(id).name("ERP").type("MOCK").enabled(true).syncIntervalMinutes(60)
                .lastSyncAt(null).build();
        when(repository.findBySyncIntervalMinutesIsNotNullAndEnabledTrue()).thenReturn(List.of(due));

        job.runScheduledSyncs();

        verify(service, times(1)).syncNow(id);
    }

    /** A connector synced more recently than its interval must be skipped. */
    @Test
    void skipsConnectorSyncedWithinInterval() {
        IntegrationConnector fresh = IntegrationConnector.builder()
                .id(UUID.randomUUID()).name("ERP").type("MOCK").enabled(true).syncIntervalMinutes(60)
                .lastSyncAt(Instant.now().minus(5, ChronoUnit.MINUTES)).build();
        when(repository.findBySyncIntervalMinutesIsNotNullAndEnabledTrue()).thenReturn(List.of(fresh));

        job.runScheduledSyncs();

        verify(service, never()).syncNow(any());
    }

    /** A failing sync must not abort the loop for the remaining connectors. */
    @Test
    void continuesAfterAFailingConnector() {
        UUID failing = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        IntegrationConnector a = IntegrationConnector.builder()
                .id(failing).name("A").type("MOCK").enabled(true).syncIntervalMinutes(60).lastSyncAt(null).build();
        IntegrationConnector b = IntegrationConnector.builder()
                .id(ok).name("B").type("MOCK").enabled(true).syncIntervalMinutes(60).lastSyncAt(null).build();
        when(repository.findBySyncIntervalMinutesIsNotNullAndEnabledTrue()).thenReturn(List.of(a, b));
        when(service.syncNow(failing)).thenThrow(new RuntimeException("boom"));

        job.runScheduledSyncs();

        verify(service, times(1)).syncNow(ok);
    }
}
