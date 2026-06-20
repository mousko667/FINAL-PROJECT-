package com.oct.invoicesystem.domain.retention.service;

import com.oct.invoicesystem.domain.retention.dto.RetentionPolicyDTO;
import com.oct.invoicesystem.domain.retention.dto.RetentionPolicyRequest;
import com.oct.invoicesystem.domain.retention.model.RetentionPolicy;
import com.oct.invoicesystem.domain.retention.repository.RetentionPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionPolicyServiceTest {

    @Mock RetentionPolicyRepository repository;
    @InjectMocks RetentionPolicyService service;

    @Test
    void get_whenTableEmpty_seedsFromDefaultAndPersists() {
        ReflectionTestUtils.setField(service, "defaultRetentionYears", 10);
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(repository.save(any(RetentionPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        RetentionPolicyDTO dto = service.get();

        assertThat(dto.retentionYears()).isEqualTo(10);
        assertThat(dto.active()).isTrue();
        verify(repository).save(any(RetentionPolicy.class));
    }

    @Test
    void get_whenRowExists_returnsItWithoutSeeding() {
        RetentionPolicy existing = RetentionPolicy.builder()
                .retentionYears(7).active(false).build();
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));

        RetentionPolicyDTO dto = service.get();

        assertThat(dto.retentionYears()).isEqualTo(7);
        assertThat(dto.active()).isFalse();
        verify(repository, never()).save(any(RetentionPolicy.class));
    }

    @Test
    void update_changesYearsAndActiveAndSetsUpdatedBy() {
        RetentionPolicy existing = RetentionPolicy.builder()
                .retentionYears(10).active(true).build();
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));
        when(repository.save(any(RetentionPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        RetentionPolicyDTO dto = service.update(new RetentionPolicyRequest(5, false), null);

        assertThat(dto.retentionYears()).isEqualTo(5);
        assertThat(dto.active()).isFalse();
    }

    @Test
    void recordSweep_writesTimestampAndCount() {
        RetentionPolicy existing = RetentionPolicy.builder()
                .retentionYears(10).active(true).build();
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));
        when(repository.save(any(RetentionPolicy.class))).thenAnswer(inv -> inv.getArgument(0));
        Instant when = Instant.parse("2026-06-20T02:30:00Z");

        service.recordSweep(when, 3);

        assertThat(existing.getLastSweepAt()).isEqualTo(when);
        assertThat(existing.getLastFlaggedCount()).isEqualTo(3);
    }

    @Test
    void getEntity_returnsManagedPolicyForJob() {
        RetentionPolicy existing = RetentionPolicy.builder()
                .retentionYears(8).active(true).build();
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));

        RetentionPolicy result = service.getEntity();

        assertThat(result.getRetentionYears()).isEqualTo(8);
    }
}
