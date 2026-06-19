package com.oct.invoicesystem.domain.payment.service;

import com.oct.invoicesystem.domain.payment.dto.PaymentAlertRuleDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentAlertRuleRequest;
import com.oct.invoicesystem.domain.payment.model.PaymentAlertRule;
import com.oct.invoicesystem.domain.payment.repository.PaymentAlertRuleRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for configurable payment due-date alert rules (B4, M7). The
 * {@link com.oct.invoicesystem.domain.notification.scheduler.DeadlineReminderJob} reads the active
 * rules to decide which J-N reminders to send.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentAlertRuleService {

    private final PaymentAlertRuleRepository repository;

    @Transactional(readOnly = true)
    public List<PaymentAlertRuleDTO> list() {
        return repository.findAllByOrderByDaysBeforeDueAsc().stream().map(this::toDto).toList();
    }

    public PaymentAlertRuleDTO create(PaymentAlertRuleRequest request, User actor) {
        repository.findByDaysBeforeDue(request.daysBeforeDue()).ifPresent(r -> {
            throw new ValidationException("error.payment_alert.duplicate_days");
        });
        PaymentAlertRule rule = PaymentAlertRule.builder()
                .daysBeforeDue(request.daysBeforeDue())
                .label(request.label())
                .active(request.active())
                .createdBy(actor)
                .build();
        return toDto(repository.save(rule));
    }

    public PaymentAlertRuleDTO update(UUID id, PaymentAlertRuleRequest request) {
        PaymentAlertRule rule = require(id);
        repository.findByDaysBeforeDue(request.daysBeforeDue())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(r -> { throw new ValidationException("error.payment_alert.duplicate_days"); });
        rule.setDaysBeforeDue(request.daysBeforeDue());
        rule.setLabel(request.label());
        rule.setActive(request.active());
        return toDto(repository.save(rule));
    }

    public void delete(UUID id) {
        repository.delete(require(id));
    }

    private PaymentAlertRule require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.payment_alert.not_found"));
    }

    private PaymentAlertRuleDTO toDto(PaymentAlertRule r) {
        return new PaymentAlertRuleDTO(r.getId(), r.getDaysBeforeDue(), r.getLabel(),
                r.isActive(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
