package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceValidationServiceTest {

    private InvoiceValidationService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceValidationService();
    }

    @Test
    void rule1_requiresDocument() {
        Invoice invoice = Invoice.builder().documents(List.of()).build();
        assertThrows(ValidationException.class, () -> service.validateHasAtLeastOneDocument(invoice));
    }

    @Test
    void rule2_rejectionReasonMinLength() {
        assertThrows(ValidationException.class, () -> service.validateRejectionReason("short"));
        assertDoesNotThrow(() -> service.validateRejectionReason("reason with 10+ chars"));
    }

    @Test
    void rule3_approverCannotApproveOwnInvoice() {
        UUID sameId = UUID.randomUUID();
        User submitter = new User();
        submitter.setId(sameId);
        User approver = new User();
        approver.setId(sameId);
        Invoice invoice = Invoice.builder().submittedBy(submitter).build();

        assertThrows(WorkflowException.class, () -> service.validateApproverIsNotSubmitter(invoice, approver));
    }

    @Test
    void rule4_resubmissionRequiresVersionIncrement() {
        assertThrows(WorkflowException.class, () -> service.validateResubmissionVersion(2, 2));
        assertDoesNotThrow(() -> service.validateResubmissionVersion(3, 2));
    }

    @Test
    void rule5_onlyDafCanApproveDafStep() {
        User nonDaf = userWithRole("ROLE_ADMIN");
        User daf = userWithRole("ROLE_DAF");
        assertThrows(ValidationException.class, () -> service.validateDafApprover(nonDaf));
        assertDoesNotThrow(() -> service.validateDafApprover(daf));
    }

    @Test
    void rule6_onlyAssistantCanCreateOrSubmit() {
        User nonAssistant = userWithRole("ROLE_ADMIN");
        User assistant = userWithRole("ROLE_ASSISTANT_COMPTABLE");
        assertThrows(ValidationException.class, () -> service.validateAssistantComptable(nonAssistant));
        assertDoesNotThrow(() -> service.validateAssistantComptable(assistant));
    }

    @Test
    void rule7_archiveMustBeAutomaticFromPaye() {
        assertThrows(WorkflowException.class, () -> service.validateArchiveIsAutomatic(InvoiceStatus.VALIDE));
        assertDoesNotThrow(() -> service.validateArchiveIsAutomatic(InvoiceStatus.PAYE));
    }

    @Test
    void rule8_hardDeleteForbidden() {
        assertThrows(WorkflowException.class, service::validateSoftDeleteOnly);
    }

    @Test
    void rule9_amountMustBeBigDecimalAndNonNegative() {
        assertThrows(ValidationException.class, () -> service.validateMonetaryAmount(new BigDecimal("-1.00")));
        assertDoesNotThrow(() -> service.validateMonetaryAmount(new BigDecimal("0.00")));
    }

    @Test
    void rule10_referenceFormatValidation() {
        assertThrows(ValidationException.class, () -> service.validateReferenceFormat("BAD-REF"));
        assertDoesNotThrow(() -> service.validateReferenceFormat("FAC-2026-00001"));
    }

    private User userWithRole(String roleName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(roleName.toLowerCase());
        user.setPassword("x");
        user.setActive(true);
        Role role = Role.builder().name(roleName).build();
        UserRole userRole = UserRole.builder().user(user).role(role).build();
        user.setUserRoles(Set.of(userRole));
        return user;
    }
}
