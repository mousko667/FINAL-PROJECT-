package com.oct.invoicesystem.domain.workflow.repository;

import com.oct.invoicesystem.domain.workflow.model.EscalationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EscalationRuleRepository extends JpaRepository<EscalationRule, UUID> {

    List<EscalationRule> findByActiveTrue();

    List<EscalationRule> findAllByOrderByHoursAfterDeadlineAsc();
}
