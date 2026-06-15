package com.oct.invoicesystem.domain.announcement.service;

import com.oct.invoicesystem.domain.announcement.dto.AnnouncementDTO;
import com.oct.invoicesystem.domain.announcement.dto.AnnouncementRequest;
import com.oct.invoicesystem.domain.announcement.model.Announcement;
import com.oct.invoicesystem.domain.announcement.repository.AnnouncementRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock private AnnouncementRepository announcementRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AnnouncementService service;

    @Test
    void create_defaultsSeverityToInfoAndPersists() {
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(i -> i.getArgument(0));

        AnnouncementDTO dto = service.create(new AnnouncementRequest("Maintenance", "Tonight 22h", null, null), null);

        assertEquals("INFO", dto.severity());
        assertEquals("Maintenance", dto.title());
    }

    @Test
    void create_rejectsInvalidSeverity() {
        assertThrows(ValidationException.class,
                () -> service.create(new AnnouncementRequest("T", "B", "URGENT", null), null));
    }

    @Test
    void create_acceptsValidSeverityCaseInsensitive() {
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(i -> i.getArgument(0));
        AnnouncementDTO dto = service.create(new AnnouncementRequest("T", "B", "warning", null), null);
        assertEquals("WARNING", dto.severity());
    }
}
