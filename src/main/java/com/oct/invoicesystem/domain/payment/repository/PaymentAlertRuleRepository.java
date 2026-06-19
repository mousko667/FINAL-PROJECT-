package com.oct.invoicesystem.domain.payment.repository;

import com.oct.invoicesystem.domain.payment.model.PaymentAlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAlertRuleRepository extends JpaRepository<PaymentAlertRule, UUID> {

    List<PaymentAlertRule> findByActiveTrue();

    List<PaymentAlertRule> findAllByOrderByDaysBeforeDueAsc();

    Optional<PaymentAlertRule> findByDaysBeforeDue(int daysBeforeDue);
}
