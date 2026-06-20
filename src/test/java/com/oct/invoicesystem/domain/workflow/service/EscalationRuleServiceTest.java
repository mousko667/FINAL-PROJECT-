package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleDTO;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleRequest;
import com.oct.invoicesystem.domain.workflow.model.EscalationRule;
import com.oct.invoicesystem.domain.workflow.repository.EscalationRuleRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalationRuleServiceTest {

    @Mock EscalationRuleRepository repository;
    @InjectMocks EscalationRuleService service;

    @Test
    void create_persistsRuleAndReturnsDto() {
        EscalationRuleRequest req = new EscalationRuleRequest(24, "After 1 day", true);
        when(repository.save(any(EscalationRule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EscalationRuleDTO dto = service.create(req, null);

        assertThat(dto.hoursAfterDeadline()).isEqualTo(24);
        assertThat(dto.label()).isEqualTo("After 1 day");
        assertThat(dto.active()).isTrue();
    }

    @Test
    void list_returnsRulesOrderedByThreshold() {
        when(repository.findAllByOrderByHoursAfterDeadlineAsc()).thenReturn(List.of(
                EscalationRule.builder().hoursAfterDeadline(0).active(true).build(),
                EscalationRule.builder().hoursAfterDeadline(48).active(false).build()));

        List<EscalationRuleDTO> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).hoursAfterDeadline()).isEqualTo(0);
    }

    @Test
    void update_missingId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new EscalationRuleRequest(12, null, true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
