package com.oct.invoicesystem.domain.announcement.service;

import com.oct.invoicesystem.domain.announcement.dto.AnnouncementDTO;
import com.oct.invoicesystem.domain.announcement.dto.AnnouncementRequest;
import com.oct.invoicesystem.domain.announcement.model.Announcement;
import com.oct.invoicesystem.domain.announcement.repository.AnnouncementRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** System announcements (M2): admin CRUD; everyone reads the active, non-expired ones. */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "CRITICAL");

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AnnouncementDTO> getActive() {
        return announcementRepository.findActive(Instant.now()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDTO> getAll() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public AnnouncementDTO create(AnnouncementRequest request, UUID creatorId) {
        String severity = normaliseSeverity(request.severity());
        User creator = creatorId == null ? null : userRepository.findById(creatorId).orElse(null);
        Announcement a = Announcement.builder()
                .title(request.title())
                .body(request.body())
                .severity(severity)
                .active(true)
                .createdBy(creator)
                .expiresAt(request.expiresAt())
                .build();
        return toDto(announcementRepository.save(a));
    }

    @Transactional
    public AnnouncementDTO update(UUID id, AnnouncementRequest request) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
        if (request.title() != null) a.setTitle(request.title());
        if (request.body() != null) a.setBody(request.body());
        if (request.severity() != null) a.setSeverity(normaliseSeverity(request.severity()));
        a.setExpiresAt(request.expiresAt());
        return toDto(announcementRepository.save(a));
    }

    @Transactional
    public void setActive(UUID id, boolean active) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
        a.setActive(active);
        announcementRepository.save(a);
    }

    @Transactional
    public void delete(UUID id) {
        if (!announcementRepository.existsById(id)) {
            throw new ResourceNotFoundException("Announcement not found: " + id);
        }
        announcementRepository.deleteById(id);
    }

    private String normaliseSeverity(String severity) {
        if (severity == null || severity.isBlank()) return "INFO";
        String s = severity.trim().toUpperCase();
        if (!SEVERITIES.contains(s)) {
            throw new ValidationException("Invalid severity (INFO|WARNING|CRITICAL): " + severity);
        }
        return s;
    }

    private AnnouncementDTO toDto(Announcement a) {
        return new AnnouncementDTO(a.getId(), a.getTitle(), a.getBody(), a.getSeverity(),
                a.isActive(), a.getCreatedAt(), a.getExpiresAt());
    }
}
