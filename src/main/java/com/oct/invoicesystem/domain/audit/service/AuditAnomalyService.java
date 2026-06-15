package com.oct.invoicesystem.domain.audit.service;

import com.oct.invoicesystem.domain.audit.dto.AuditAnomalyDTO;
import com.oct.invoicesystem.domain.audit.repository.AuditLogRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Statistical (non-ML) audit anomaly detection (M10). Over a recent window it flags:
 *  - HIGH_VOLUME: users whose action count exceeds mean + k·stddev of the population, and
 *  - EXCESSIVE_ACCESS_DENIED: users with too many ACCESS_DENIED events.
 * Deliberately simple + explainable — suitable for an audit dashboard, no model training.
 */
@Service
@RequiredArgsConstructor
public class AuditAnomalyService {

    @Value("${app.audit.anomaly.windowHours:24}")
    private int windowHours;

    @Value("${app.audit.anomaly.sigma:3.0}")
    private double sigma;

    @Value("${app.audit.anomaly.accessDeniedThreshold:5}")
    private long accessDeniedThreshold;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AuditAnomalyDTO> detectAnomalies() {
        Instant since = Instant.now().minus(windowHours, ChronoUnit.HOURS);
        List<AuditAnomalyDTO> anomalies = new ArrayList<>();

        // ── HIGH_VOLUME: per-user total activity vs population mean + k·stddev ──
        Map<UUID, Long> perUser = new HashMap<>();
        for (Object[] row : auditLogRepository.countByUserSince(since)) {
            perUser.put((UUID) row[0], (Long) row[1]);
        }
        if (perUser.size() >= 3) { // need a meaningful population to compute a baseline
            // Leave-one-out: a user's baseline is the mean+kσ of EVERY OTHER user, so a single
            // extreme actor doesn't inflate its own threshold and escape detection.
            for (Map.Entry<UUID, Long> candidate : perUser.entrySet()) {
                List<Long> others = perUser.entrySet().stream()
                        .filter(e -> !e.getKey().equals(candidate.getKey()))
                        .map(Map.Entry::getValue).toList();
                double mean = others.stream().mapToLong(Long::longValue).average().orElse(0);
                double variance = others.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
                double std = Math.sqrt(variance);
                double threshold = mean + sigma * std;
                if (candidate.getValue() > threshold && candidate.getValue() > mean) {
                    anomalies.add(new AuditAnomalyDTO(candidate.getKey(), username(candidate.getKey()), "HIGH_VOLUME",
                            candidate.getValue(), round(mean),
                            "Activity (" + candidate.getValue() + ") exceeds peers' mean+" + sigma + "σ (" + round(threshold) + ")"));
                }
            }
        }

        // ── EXCESSIVE_ACCESS_DENIED: per-user 403/401 count over a fixed threshold ──
        for (Object[] row : auditLogRepository.countByUserSinceAndAction(since, "ACCESS_DENIED")) {
            UUID userId = (UUID) row[0];
            long count = (Long) row[1];
            if (count >= accessDeniedThreshold) {
                anomalies.add(new AuditAnomalyDTO(userId, username(userId), "EXCESSIVE_ACCESS_DENIED",
                        count, accessDeniedThreshold,
                        count + " access-denied events in " + windowHours + "h (threshold " + accessDeniedThreshold + ")"));
            }
        }
        return anomalies;
    }

    private String username(UUID userId) {
        return userRepository.findById(userId).map(u -> u.getUsername()).orElse(userId.toString());
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
