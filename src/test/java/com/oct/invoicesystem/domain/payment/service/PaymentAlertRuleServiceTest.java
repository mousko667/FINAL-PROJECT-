package com.oct.invoicesystem.domain.payment.service;

import com.oct.invoicesystem.domain.payment.dto.PaymentAlertRuleDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentAlertRuleRequest;
import com.oct.invoicesystem.domain.payment.repository.PaymentAlertRuleRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration coverage for {@link PaymentAlertRuleService} (B4): CRUD plus the unique-days rule.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentAlertRuleServiceTest {

    @Autowired private PaymentAlertRuleService service;
    @Autowired private PaymentAlertRuleRepository repository;

    @Test
    void create_persistsRule_andListsSortedByDays() {
        service.create(new PaymentAlertRuleRequest(7, "J-7", true), null);
        service.create(new PaymentAlertRuleRequest(1, "J-1", true), null);
        service.create(new PaymentAlertRuleRequest(3, "J-3", false), null);

        assertThat(service.list()).extracting(PaymentAlertRuleDTO::daysBeforeDue)
                .containsExactly(1, 3, 7); // ordered ascending
        assertThat(repository.findByActiveTrue()).extracting("daysBeforeDue")
                .containsExactlyInAnyOrder(7, 1); // J-3 is inactive
    }

    @Test
    void create_rejectsDuplicateDays() {
        service.create(new PaymentAlertRuleRequest(5, "first", true), null);
        assertThatThrownBy(() -> service.create(new PaymentAlertRuleRequest(5, "dup", true), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_changesDaysAndActive() {
        PaymentAlertRuleDTO created = service.create(new PaymentAlertRuleRequest(10, "ten", true), null);
        PaymentAlertRuleDTO updated = service.update(created.id(), new PaymentAlertRuleRequest(14, "fortnight", false));

        assertThat(updated.daysBeforeDue()).isEqualTo(14);
        assertThat(updated.active()).isFalse();
    }
}
