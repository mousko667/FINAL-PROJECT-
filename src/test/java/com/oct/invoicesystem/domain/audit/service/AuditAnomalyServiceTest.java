package com.oct.invoicesystem.domain.audit.service;

import com.oct.invoicesystem.domain.audit.dto.AuditAnomalyDTO;
import com.oct.invoicesystem.domain.audit.repository.AuditLogRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAnomalyServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AuditAnomalyService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "windowHours", 24);
        ReflectionTestUtils.setField(service, "sigma", 3.0);
        ReflectionTestUtils.setField(service, "accessDeniedThreshold", 5L);
        lenient().when(userRepository.findById(any())).thenAnswer(i -> {
            User u = new User(); u.setId(i.getArgument(0)); u.setUsername("user-" + i.getArgument(0).toString().substring(0, 4));
            return Optional.of(u);
        });
    }

    @Test
    void detectsHighVolumeOutlier() {
        UUID outlier = UUID.randomUUID();
        // population: four users ~5 actions, one user with 500 → clear > mean+3σ
        when(auditLogRepository.countByUserSince(any())).thenReturn(List.of(
                new Object[]{UUID.randomUUID(), 5L},
                new Object[]{UUID.randomUUID(), 6L},
                new Object[]{UUID.randomUUID(), 4L},
                new Object[]{UUID.randomUUID(), 5L},
                new Object[]{outlier, 500L}));
        when(auditLogRepository.countByUserSinceAndAction(any(), eq("ACCESS_DENIED"))).thenReturn(List.of());

        List<AuditAnomalyDTO> result = service.detectAnomalies();

        assertTrue(result.stream().anyMatch(a -> a.userId().equals(outlier) && a.type().equals("HIGH_VOLUME")));
    }

    @Test
    void detectsExcessiveAccessDenied() {
        UUID attacker = UUID.randomUUID();
        when(auditLogRepository.countByUserSince(any())).thenReturn(List.of());
        when(auditLogRepository.countByUserSinceAndAction(any(), eq("ACCESS_DENIED")))
                .thenReturn(List.<Object[]>of(new Object[]{attacker, 9L}));

        List<AuditAnomalyDTO> result = service.detectAnomalies();

        assertEquals(1, result.size());
        assertEquals("EXCESSIVE_ACCESS_DENIED", result.get(0).type());
        assertEquals(9L, result.get(0).observed());
    }

    @Test
    void noAnomalies_whenActivityUniform() {
        when(auditLogRepository.countByUserSince(any())).thenReturn(List.of(
                new Object[]{UUID.randomUUID(), 5L},
                new Object[]{UUID.randomUUID(), 5L},
                new Object[]{UUID.randomUUID(), 5L}));
        when(auditLogRepository.countByUserSinceAndAction(any(), eq("ACCESS_DENIED"))).thenReturn(List.of());

        assertTrue(service.detectAnomalies().isEmpty());
    }
}
