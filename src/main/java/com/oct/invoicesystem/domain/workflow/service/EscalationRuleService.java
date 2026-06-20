package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleDTO;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleRequest;
import com.oct.invoicesystem.domain.workflow.model.EscalationRule;
import com.oct.invoicesystem.domain.workflow.repository.EscalationRuleRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for configurable SLA escalation rules (B1). The
 * {@link com.oct.invoicesystem.domain.notification.scheduler.DeadlineReminderJob} reads the active
 * rules to decide how long after the deadline an overdue approval step is escalated.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EscalationRuleService {

    private final EscalationRuleRepository repository;

    @Transactional(readOnly = true)
    public List<EscalationRuleDTO> list() {
        return repository.findAllByOrderByHoursAfterDeadlineAsc().stream().map(this::toDto).toList();
    }

    public EscalationRuleDTO create(EscalationRuleRequest request, User actor) {
        EscalationRule rule = EscalationRule.builder()
                .hoursAfterDeadline(request.hoursAfterDeadline())
                .label(request.label())
                .active(request.active())
                .createdBy(actor)
                .build();
        return toDto(repository.save(rule));
    }

    public EscalationRuleDTO update(UUID id, EscalationRuleRequest request) {
        EscalationRule rule = require(id);
        rule.setHoursAfterDeadline(request.hoursAfterDeadline());
        rule.setLabel(request.label());
        rule.setActive(request.active());
        return toDto(repository.save(rule));
    }

    public void delete(UUID id) {
        repository.delete(require(id));
    }

    private EscalationRule require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.escalation_rule.not_found"));
    }

    private EscalationRuleDTO toDto(EscalationRule r) {
        return new EscalationRuleDTO(r.getId(), r.getHoursAfterDeadline(), r.getLabel(),
                r.isActive(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
