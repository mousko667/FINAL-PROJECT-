package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final com.oct.invoicesystem.domain.department.repository.DepartmentRepository departmentRepository;

    /**
     * Crée une délégation à partir des identifiants du délégant et du délégataire.
     * Résout les utilisateurs via {@link UserRepository} (logique déplacée hors du contrôleur,
     * règle absolue P1-05) avant de déléguer à {@link #createDelegation(User, User, String, LocalDate, LocalDate, String, User)}.
     *
     * @throws ResourceNotFoundException si le délégant ou le délégataire n'existe pas
     */
    @Transactional
    public ApprovalDelegation createDelegation(
            UUID delegatorId, UUID delegateeId, String departmentCode,
            LocalDate fromDate, LocalDate toDate, String reason, User createdBy) {
        User delegator = userRepository.findById(delegatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Delegator not found"));
        User delegatee = userRepository.findById(delegateeId)
                .orElseThrow(() -> new ResourceNotFoundException("Delegatee not found"));
        return createDelegation(delegator, delegatee, departmentCode, fromDate, toDate, reason, createdBy);
    }

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

    /**
     * Self-service (M6): the current approver delegates THEIR OWN approvals while absent.
     * The delegator is the caller; the department defaults to the caller's own department
     * (an explicit departmentCode may still be passed, e.g. for multi-dept approvers).
     */
    @Transactional
    public ApprovalDelegation createSelfDelegation(
            User delegator, UUID delegateeId, String departmentCode,
            LocalDate fromDate, LocalDate toDate, String reason) {
        User delegatee = userRepository.findById(delegateeId)
                .orElseThrow(() -> new ResourceNotFoundException("Delegatee not found"));
        String dept = departmentCode;
        if (dept == null || dept.isBlank()) {
            if (delegator.getDepartmentId() == null) {
                throw new ValidationException("Aucun département associé à votre compte; précisez-en un.");
            }
            dept = departmentRepository.findById(delegator.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"))
                    .getCode();
        }
        // createdBy = delegator (self-service, no admin involved)
        return createDelegation(delegator, delegatee, dept, fromDate, toDate, reason, delegator);
    }

    public List<ApprovalDelegation> getMyDelegations(UUID delegatorId) {
        return delegationRepository.findByDelegatorIdOrderByCreatedAtDesc(delegatorId);
    }

    /**
     * Candidates a delegator can delegate to: active staff (non-supplier), excluding the caller.
     * Returns {id, username, fullName} maps so an approver can pick a delegatee without needing
     * the admin-only GET /users.
     */
    public List<java.util.Map<String, String>> getEligibleDelegatees(UUID excludeUserId) {
        return userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.getSupplier() == null)
                .filter(u -> !u.getId().equals(excludeUserId))
                .map(u -> java.util.Map.of(
                        "id", u.getId().toString(),
                        "username", u.getUsername(),
                        "fullName", ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                                + (u.getLastName() == null ? "" : u.getLastName())).trim()))
                .sorted(java.util.Comparator.comparing(m -> m.get("username")))
                .toList();
    }

    /**
     * Revoke a delegation the caller owns (delegator). Prevents revoking someone else's delegation.
     */
    @Transactional
    public void revokeOwnDelegation(UUID delegationId, UUID requesterId) {
        ApprovalDelegation d = delegationRepository.findById(delegationId)
                .orElseThrow(() -> new ResourceNotFoundException("Delegation not found: " + delegationId));
        if (!d.getDelegator().getId().equals(requesterId)) {
            throw new ValidationException("Vous ne pouvez révoquer que vos propres délégations");
        }
        d.setRevoked(true);
        d.setRevokedAt(Instant.now());
        delegationRepository.save(d);
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
