package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalDelegationRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelegationServiceTest {

    @Mock ApprovalDelegationRepository delegationRepository;
    @Mock UserRepository userRepository;
    @Mock DepartmentRepository departmentRepository;
    @InjectMocks DelegationService delegationService;

    /** A delegator that actually holds the validator role for {@code deptCode} (N13 precondition). */
    private User validatorFor(String deptCode) {
        User u = User.builder().id(UUID.randomUUID()).username("val-" + deptCode.toLowerCase()).build();
        u.setUserRoles(Set.of(UserRole.builder()
                .role(Role.builder().name("ROLE_VALIDATEUR_N1_" + deptCode).build())
                .user(u)
                .build()));
        return u;
    }

    private User userWithRole(String roleName) {
        User u = User.builder().id(UUID.randomUUID()).username(roleName.toLowerCase()).build();
        u.setUserRoles(Set.of(UserRole.builder()
                .role(Role.builder().name(roleName).build()).user(u).build()));
        return u;
    }

    private void stubDepartmentExists(String code) {
        when(departmentRepository.findByCode(code))
                .thenReturn(Optional.of(Department.builder().id(UUID.randomUUID()).code(code).build()));
    }

    @Test
    @DisplayName("createDelegation: auto-délégation interdit")
    void createDelegation_selfDelegation_throwsValidationException() {
        User user = validatorFor("INFO");
        assertThatThrownBy(() ->
                delegationService.createDelegation(user, user, "INFO",
                        LocalDate.now(), LocalDate.now().plusDays(7), "congés", user))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("createDelegation: date fin avant date début interdit")
    void createDelegation_invalidDates_throwsValidationException() {
        User delegator = validatorFor("INFO");
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").build();
        assertThatThrownBy(() ->
                delegationService.createDelegation(delegator, delegatee, "INFO",
                        LocalDate.now().plusDays(7), LocalDate.now(), "test", delegator))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("createDelegation: délégation valide persistée (délégant détient le rôle du dept)")
    void createDelegation_valid_persistsDelegation() {
        User delegator = validatorFor("INFO");
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").build();
        stubDepartmentExists("INFO");
        ApprovalDelegation saved = ApprovalDelegation.builder()
                .id(UUID.randomUUID()).delegator(delegator).delegatee(delegatee)
                .departmentCode("INFO").fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(7)).build();
        when(delegationRepository.save(any())).thenReturn(saved);

        ApprovalDelegation result = delegationService.createDelegation(
                delegator, delegatee, "INFO",
                LocalDate.now(), LocalDate.now().plusDays(7), "congés annuels", delegator);

        assertThat(result.getId()).isNotNull();
        verify(delegationRepository).save(any(ApprovalDelegation.class));
    }

    @Test
    @DisplayName("createDelegation(byIds): résout les utilisateurs et persiste (P1-05)")
    void createDelegationByIds_valid_resolvesUsersAndPersists() {
        User delegator = validatorFor("INFO");
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").build();
        User admin = User.builder().id(UUID.randomUUID()).username("admin").build();
        when(userRepository.findById(delegator.getId())).thenReturn(Optional.of(delegator));
        when(userRepository.findById(delegatee.getId())).thenReturn(Optional.of(delegatee));
        stubDepartmentExists("INFO");
        ApprovalDelegation saved = ApprovalDelegation.builder()
                .id(UUID.randomUUID()).delegator(delegator).delegatee(delegatee)
                .departmentCode("INFO").fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(7)).build();
        when(delegationRepository.save(any())).thenReturn(saved);

        ApprovalDelegation result = delegationService.createDelegation(
                delegator.getId(), delegatee.getId(), "INFO",
                LocalDate.now(), LocalDate.now().plusDays(7), "congés annuels", admin);

        assertThat(result.getId()).isNotNull();
        verify(userRepository).findById(delegator.getId());
        verify(userRepository).findById(delegatee.getId());
        verify(delegationRepository).save(any(ApprovalDelegation.class));
    }

    @Test
    @DisplayName("createDelegation(byIds): délégant introuvable -> ResourceNotFoundException")
    void createDelegationByIds_delegatorNotFound_throwsResourceNotFound() {
        UUID delegatorId = UUID.randomUUID();
        UUID delegateeId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).username("admin").build();
        when(userRepository.findById(delegatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                delegationService.createDelegation(delegatorId, delegateeId, "INFO",
                        LocalDate.now(), LocalDate.now().plusDays(7), "congés", admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Delegator");
        verify(delegationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDelegation(byIds): délégataire introuvable -> ResourceNotFoundException")
    void createDelegationByIds_delegateeNotFound_throwsResourceNotFound() {
        UUID delegatorId = UUID.randomUUID();
        UUID delegateeId = UUID.randomUUID();
        User delegator = User.builder().id(delegatorId).username("a").build();
        User admin = User.builder().id(UUID.randomUUID()).username("admin").build();
        when(userRepository.findById(delegatorId)).thenReturn(Optional.of(delegator));
        when(userRepository.findById(delegateeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                delegationService.createDelegation(delegatorId, delegateeId, "INFO",
                        LocalDate.now(), LocalDate.now().plusDays(7), "congés", admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Delegatee");
        verify(delegationRepository, never()).save(any());
    }

    // ---- N13: department/role validation on delegation creation ----

    @Test
    @DisplayName("N13: déléguer 'DAF' est refusé si le délégant n'a pas ROLE_DAF")
    void createDelegation_dafByNonDaf_throwsValidationException() {
        User validator = validatorFor("INFO"); // no ROLE_DAF
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").build();

        assertThatThrownBy(() ->
                delegationService.createDelegation(validator, delegatee, "DAF",
                        LocalDate.now(), LocalDate.now().plusDays(7), "abus", validator))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("daf_requires_daf_role");
        verify(delegationRepository, never()).save(any());
    }

    @Test
    @DisplayName("N13: déléguer 'DAF' est autorisé quand le délégant détient réellement ROLE_DAF")
    void createDelegation_dafByDaf_persists() {
        User daf = userWithRole("ROLE_DAF");
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").build();
        when(delegationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalDelegation result = delegationService.createDelegation(
                daf, delegatee, "DAF", LocalDate.now(), LocalDate.now().plusDays(7), "congés", daf);

        assertThat(result.getDepartmentCode()).isEqualTo("DAF");
        verify(delegationRepository).save(any(ApprovalDelegation.class));
    }

    @Test
    @DisplayName("N13: déléguer un département que le délégant ne valide pas est refusé")
    void createDelegation_foreignDepartment_throwsValidationException() {
        User drhValidator = validatorFor("DRH"); // holds DRH, not INFO
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").build();
        stubDepartmentExists("INFO");

        assertThatThrownBy(() ->
                delegationService.createDelegation(drhValidator, delegatee, "INFO",
                        LocalDate.now(), LocalDate.now().plusDays(7), "abus", drhValidator))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not_delegator_role");
        verify(delegationRepository, never()).save(any());
    }

    @Test
    @DisplayName("N13: département inexistant est refusé")
    void createDelegation_unknownDepartment_throwsValidationException() {
        User validator = validatorFor("INFO");
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").build();
        when(departmentRepository.findByCode("ZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                delegationService.createDelegation(validator, delegatee, "ZZZ",
                        LocalDate.now(), LocalDate.now().plusDays(7), "x", validator))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("invalid_department");
        verify(delegationRepository, never()).save(any());
    }
}
