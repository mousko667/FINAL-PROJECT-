package com.oct.invoicesystem.domain.auth.service;

import com.oct.invoicesystem.domain.auth.repository.ActiveSessionRepository;
import com.oct.invoicesystem.domain.user.dto.ActiveSessionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing active user sessions (admin operations).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminSessionService {

    private final ActiveSessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public List<ActiveSessionDTO> listActiveSessions() {
        return sessionRepository.findAllActive(Instant.now()).stream()
                .map(s -> new ActiveSessionDTO(
                        s.getId(),
                        s.getUser().getId(),
                        s.getUser().getUsername(),
                        s.getIpAddress(),
                        s.getCreatedAt(),
                        s.getExpiresAt()
                ))
                .toList();
    }

    public void revokeUserSessions(UUID userId) {
        log.info("Revoking all sessions for user {}", userId);
        sessionRepository.revokeAllForUser(userId, Instant.now());
    }
}
