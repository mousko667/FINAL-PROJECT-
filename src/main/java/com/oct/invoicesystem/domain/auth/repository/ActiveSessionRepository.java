package com.oct.invoicesystem.domain.auth.repository;

import com.oct.invoicesystem.domain.auth.model.ActiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActiveSessionRepository extends JpaRepository<ActiveSession, UUID> {

    Optional<ActiveSession> findByRefreshToken(String refreshToken);

    @Query("SELECT s FROM ActiveSession s WHERE s.user.id = :userId AND s.revoked = false AND s.expiresAt > :now")
    List<ActiveSession> findActiveByUserId(UUID userId, Instant now);

    @Query("SELECT s FROM ActiveSession s WHERE s.revoked = false AND s.expiresAt > :now ORDER BY s.createdAt DESC")
    List<ActiveSession> findAllActive(Instant now);

    @Modifying
    @Query("UPDATE ActiveSession s SET s.revoked = true, s.revokedAt = :now WHERE s.user.id = :userId AND s.revoked = false")
    void revokeAllForUser(UUID userId, Instant now);

    @Modifying
    @Query("UPDATE ActiveSession s SET s.revoked = true, s.revokedAt = :now WHERE s.refreshToken = :token")
    void revokeByToken(String token, Instant now);
}
