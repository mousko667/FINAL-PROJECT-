package com.oct.invoicesystem.domain.workflow.guard;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleMatchGuard implements Guard<InvoiceStatus, InvoiceEvent> {

    private final UserRepository userRepository;

    @Override
    public boolean evaluate(StateContext<InvoiceStatus, InvoiceEvent> context) {
        UUID userId = (UUID) context.getExtendedState().getVariables().get(WorkflowExtendedStateKeys.USER_ID);
        if (userId == null) {
            log.warn("RoleMatchGuard: No user ID in extended state");
            return false;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        Department dept = context.getExtendedState().get(WorkflowExtendedStateKeys.DEPARTMENT, Department.class);
        if (dept == null) {
            log.error("RoleMatchGuard: Department is required in extended state");
            return false;
        }

        InvoiceEvent event = context.getEvent();
        String requiredRole = null;

        if (event == InvoiceEvent.ASSIGN_AA) {
            boolean isAa = user.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ASSISTANT_COMPTABLE"));
            if (!isAa) {
                log.warn("RoleMatchGuard: user {} is not ROLE_ASSISTANT_COMPTABLE", userId);
            }
            return isAa;
        }

        if (event == InvoiceEvent.ASSIGN_REVIEWER || event == InvoiceEvent.VALIDATE_N1) {
            requiredRole = dept.getN1Role();
        } else if (event == InvoiceEvent.VALIDATE_N2) {
            requiredRole = dept.getN2Role();
        } else if (event == InvoiceEvent.BON_A_PAYER) {
            requiredRole = "ROLE_DAF";
        } else if (event == InvoiceEvent.REJECT) {
            // Rejection role validation logic
            // To simplify, if it's currently at N1, it should be N1 throwing rejection.
            if (context.getSource().getId() == InvoiceStatus.EN_VALIDATION_N1) requiredRole = dept.getN1Role();
            else if (context.getSource().getId() == InvoiceStatus.EN_VALIDATION_N2) requiredRole = dept.getN2Role();
            else if (context.getSource().getId() == InvoiceStatus.VALIDE) requiredRole = "ROLE_DAF";
            else if (context.getSource().getId() == InvoiceStatus.EN_CONTROLE_AA) requiredRole = "ROLE_ASSISTANT_COMPTABLE";
        }

        if (requiredRole != null) {
            String finalRequiredRole = requiredRole;
            boolean hasRole = user.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals(finalRequiredRole));
            if (!hasRole) {
                log.warn("User {} denied, expected role {}", user.getUsername(), requiredRole);
                throw new AccessDeniedException("User does not have required role: " + requiredRole);
            }
        }

        return true;
    }
}
