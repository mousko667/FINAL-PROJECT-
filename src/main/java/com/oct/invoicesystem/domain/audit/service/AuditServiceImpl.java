package com.oct.invoicesystem.domain.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.audit.dto.AuditLogDTO;
import com.oct.invoicesystem.domain.audit.model.AuditLog;
import com.oct.invoicesystem.domain.audit.repository.AuditLogRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final String USER_FALLBACK_LABEL = "—";
    private static final int TOP_USERS = 10;

    @Async
    @Override
    @Transactional
    public void logAction(UUID userId, String entityType, String entityId, String action, Object oldValue, Object newValue, String ipAddress, String userAgent) {
        log.debug("Async writing audit log for entityType={} entityId={} action={}", entityType, entityId, action);
        try {
            User user = null;
            if (userId != null) {
                user = userRepository.findById(userId).orElse(null);
            }

            String oldValStr = null;
            if (oldValue != null) {
                oldValStr = objectMapper.writeValueAsString(oldValue);
            }

            String newValStr = null;
            if (newValue != null) {
                newValStr = objectMapper.writeValueAsString(newValue);
            }

            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .oldValue(oldValStr)
                    .newValue(newValStr)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            auditLogRepository.save(auditLog);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit log values for entity: {}", entityId, e);
            // We eat the exception so the caller transaction does NOT fail, but we've lost this audit log part.
            // Still save without values if desired, or skip. We will skip or save without values:
            try {
                AuditLog fallback = AuditLog.builder()
                        .user(userId != null ? userRepository.findById(userId).orElse(null) : null)
                        .entityType(entityType)
                        .entityId(entityId)
                        .action(action)
                        .oldValue("{\"error\": \"serialization_failed\"}")
                        .newValue("{\"error\": \"serialization_failed\"}")
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .build();
                auditLogRepository.save(fallback);
            } catch (Exception ex) {
                log.error("Fallback audit log save failed", ex);
            }
        } catch (Exception e) {
            log.error("Unexpected error saving audit log", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogDTO> searchLogs(UUID userId, String entityType, String entityId, String action, java.time.Instant from, java.time.Instant to, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(null);
        
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("user").get("id"), userId));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null && !entityId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        
        return auditLogRepository.findAll(spec, pageable).map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogDTO> searchLogsWithActionFilter(UUID userId, String entityType, String entityId, String action, List<String> allowedActions, java.time.Instant from, java.time.Instant to, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where((root, query, cb) -> root.get("action").in(allowedActions));
        
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("user").get("id"), userId));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null && !entityId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.upper(root.get("action")), "%" + action.toUpperCase() + "%"));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        
        return auditLogRepository.findAll(spec, pageable).map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO summarize(
            java.time.LocalDate from, java.time.LocalDate to, java.util.List<String> allowedActions) {
        java.time.Instant fromI = from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        java.time.Instant toI = to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(); // borne haute exclusive

        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> byAction =
                toEntries(auditLogRepository.summaryByAction(fromI, toI, allowedActions), false);
        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> byUser =
                toEntries(auditLogRepository.summaryByUser(fromI, toI, allowedActions), true);
        if (byUser.size() > TOP_USERS) byUser = byUser.subList(0, TOP_USERS);
        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> byEntityType =
                toEntries(auditLogRepository.summaryByEntityType(fromI, toI, allowedActions), false);
        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> byDay =
                toEntries(auditLogRepository.summaryByDay(fromI, toI, allowedActions), false);

        long total = byAction.stream().mapToLong(com.oct.invoicesystem.domain.audit.dto.CountEntry::count).sum();
        return new com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO(
                from, to, total, byAction, byUser, byEntityType, byDay);
    }

    private java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> toEntries(
            java.util.List<Object[]> rows, boolean fallbackNull) {
        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> out = new java.util.ArrayList<>();
        for (Object[] r : rows) {
            String label = r[0] == null ? (fallbackNull ? USER_FALLBACK_LABEL : "") : String.valueOf(r[0]);
            long count = ((Number) r[1]).longValue();
            out.add(new com.oct.invoicesystem.domain.audit.dto.CountEntry(label, count));
        }
        return out;
    }

    private AuditLogDTO toDTO(AuditLog log) {
        return new AuditLogDTO(
                log.getId(),
                log.getUser() != null ? log.getUser().getId() : null,
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getOldValue(),
                log.getNewValue(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt()
        );
    }
}
