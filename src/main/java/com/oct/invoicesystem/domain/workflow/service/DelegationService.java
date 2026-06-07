package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalDelegationRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelegationService {

    private final ApprovalDelegationRepository delegationRepository;

    @Transactional
    public ApprovalDelegation createDelegation(
            User delegator, User delegatee, String departmentCode,
            LocalDate fromDate, LocalDate toDate, String reason, User createdBy) {
        if (delegator.getId().equals(delegatee.getId())) {
            throw new ValidationException("Un utilisateur ne peut pas se déléguer à lui-même");
        }
        if (toDate.isBefore(fromDate)) {
            throw new ValidationException("La date de fin doit être après la date de début");
        }
        ApprovalDelegation delegation = ApprovalDelegation.builder()
                .delegator(delegator)
                .delegatee(delegatee)
                .departmentCode(departmentCode)
                .fromDate(fromDate)
                .toDate(toDate)
                .reason(reason)
                .createdBy(createdBy)
                .build();
        log.info("Delegation created: {} delegates to {} for dept {} from {} to {}",
                delegator.getUsername(), delegatee.getUsername(), departmentCode, fromDate, toDate);
        return delegationRepository.save(delegation);
    }

    public List<ApprovalDelegation> getActiveDelegationsForDepartment(String departmentCode) {
        return delegationRepository.findActiveDelegationsForDepartment(departmentCode, LocalDate.now());
    }

    @Transactional
    public void revokeDelegation(UUID delegationId) {
        ApprovalDelegation d = delegationRepository.findById(delegationId)
                .orElseThrow(() -> new ResourceNotFoundException("Delegation not found: " + delegationId));
        d.setRevoked(true);
        d.setRevokedAt(Instant.now());
        delegationRepository.save(d);
    }
}
