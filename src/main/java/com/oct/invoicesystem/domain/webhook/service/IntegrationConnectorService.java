package com.oct.invoicesystem.domain.webhook.service;

import com.oct.invoicesystem.domain.webhook.dto.IntegrationConnectorDTO;
import com.oct.invoicesystem.domain.webhook.model.IntegrationConnector;
import com.oct.invoicesystem.domain.webhook.repository.IntegrationConnectorRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Integration connector framework (M12). Generic CRUD over configurable connectors plus a
 * "test connection" action. A built-in MOCK type reports healthy without a real backend (so the
 * feature is demonstrable); real types (ERP/ACCOUNTING/BANKING/DMS) probe their endpoint with a
 * short-timeout HTTP HEAD and record the resulting status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationConnectorService {

    private static final Set<String> TYPES = Set.of("ERP", "ACCOUNTING", "BANKING", "DMS", "MOCK");
    private static final int TIMEOUT_MS = 3000;

    private final IntegrationConnectorRepository repository;

    @Transactional(readOnly = true)
    public List<IntegrationConnectorDTO.Response> list() {
        return repository.findByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public IntegrationConnectorDTO.Response create(IntegrationConnectorDTO.Request req, UUID actorId) {
        String type = req.type() == null ? "" : req.type().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new ValidationException("Invalid connector type (ERP|ACCOUNTING|BANKING|DMS|MOCK): " + req.type());
        }
        IntegrationConnector c = IntegrationConnector.builder()
                .name(req.name()).type(type).endpoint(req.endpoint()).config(req.config())
                .enabled(true).lastStatus("UNKNOWN").createdBy(actorId).build();
        return toDto(repository.save(c));
    }

    @Transactional
    public void setEnabled(UUID id, boolean enabled) {
        IntegrationConnector c = get(id);
        c.setEnabled(enabled);
        repository.save(c);
    }

    /**
     * Configures the scheduled-sync interval (B6, M12 #10). A {@code null} interval disables
     * automatic synchronisation; a positive value (minutes) enables the {@code ConnectorSyncJob}
     * to sync this connector once the interval has elapsed since {@code lastSyncAt}.
     */
    @Transactional
    public IntegrationConnectorDTO.Response updateSchedule(UUID id, Integer intervalMinutes) {
        if (intervalMinutes != null && intervalMinutes <= 0) {
            throw new ValidationException("Sync interval must be a positive number of minutes");
        }
        IntegrationConnector c = get(id);
        c.setSyncIntervalMinutes(intervalMinutes);
        return toDto(repository.save(c));
    }

    /**
     * Runs a synchronisation now and records the outcome on the connector (B6). As M12 connectors
     * are a configurable framework (no live external backend), the sync reuses the same connectivity
     * probe as {@link #testConnection(UUID)}: a healthy probe is recorded as SUCCESS, otherwise FAILED.
     * The orchestration (scheduling, triggering, outcome log) is real; only the exchanged payload is
     * out of scope until a real connector is plugged in.
     */
    @Transactional
    public IntegrationConnectorDTO.Response syncNow(UUID id) {
        IntegrationConnectorDTO.Response probe = testConnection(id);
        IntegrationConnector c = get(id);
        boolean ok = "UP".equals(probe.lastStatus());
        c.setLastSyncStatus(ok ? "SUCCESS" : "FAILED");
        c.setLastSyncMessage(ok ? "Sync completed (framework probe): " + probe.lastMessage()
                                : "Sync failed: " + probe.lastMessage());
        c.setLastSyncAt(Instant.now());
        return toDto(repository.save(c));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Connector not found: " + id);
        repository.deleteById(id);
    }

    /** Tests connectivity and records the result on the connector. */
    @Transactional
    public IntegrationConnectorDTO.Response testConnection(UUID id) {
        IntegrationConnector c = get(id);
        String status;
        String message;
        if ("MOCK".equals(c.getType())) {
            status = "UP";
            message = "Mock connector — simulated healthy connection.";
        } else if (c.getEndpoint() == null || c.getEndpoint().isBlank()) {
            status = "UNKNOWN";
            message = "No endpoint configured.";
        } else {
            try {
                URI uri = URI.create(c.getEndpoint());
                assertSafeUrl(uri); // SSRF guard: http/https only, no loopback/link-local/private/metadata hosts
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("HEAD");
                conn.setInstanceFollowRedirects(false); // don't follow redirects to an internal target
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                int code = conn.getResponseCode();
                conn.disconnect();
                status = (code >= 200 && code < 500) ? "UP" : "DOWN";
                message = "HTTP " + code;
            } catch (ValidationException ve) {
                status = "DOWN";
                message = ve.getMessage();
            } catch (Exception e) {
                status = "DOWN";
                message = "Connection failed: " + e.getClass().getSimpleName();
            }
        }
        c.setLastStatus(status);
        c.setLastMessage(message);
        c.setLastCheckedAt(Instant.now());
        return toDto(repository.save(c));
    }

    /**
     * SSRF guard for the test-connection probe: only http/https, and the resolved host must not be
     * a loopback / link-local (incl. 169.254.169.254 cloud metadata) / private (RFC1918) / wildcard
     * / multicast address. Admin-only, but the endpoint is user-supplied so we validate anyway.
     */
    private void assertSafeUrl(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ValidationException("Endpoint must be http(s)");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException("Endpoint host is invalid");
        }
        try {
            for (java.net.InetAddress addr : java.net.InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                        || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                    throw new ValidationException("Endpoint resolves to a non-routable/internal address");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new ValidationException("Endpoint host could not be resolved");
        }
    }

    private IntegrationConnector get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connector not found: " + id));
    }

    private IntegrationConnectorDTO.Response toDto(IntegrationConnector c) {
        return new IntegrationConnectorDTO.Response(c.getId(), c.getName(), c.getType(), c.getEndpoint(),
                c.isEnabled(), c.getLastStatus(), c.getLastCheckedAt(), c.getLastMessage(),
                c.getSyncIntervalMinutes(), c.getLastSyncAt(), c.getLastSyncStatus(), c.getLastSyncMessage(),
                c.getCreatedAt());
    }
}
