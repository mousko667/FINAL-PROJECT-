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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

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
    public Page<AuditLogDTO> searchLogs(UUID userId, String entityType, String entityId, String action, Pageable pageable) {
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
        
        return auditLogRepository.findAll(spec, pageable).map(this::toDTO);
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
