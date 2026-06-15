package com.oct.invoicesystem.domain.announcement.repository;

import com.oct.invoicesystem.domain.announcement.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    @Query("""
            SELECT a FROM Announcement a
            WHERE a.active = true
              AND (a.expiresAt IS NULL OR a.expiresAt > :now)
            ORDER BY a.createdAt DESC
            """)
    List<Announcement> findActive(@Param("now") Instant now);

    List<Announcement> findAllByOrderByCreatedAtDesc();
}
