package com.oct.invoicesystem.domain.notification.scheduler;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.payment.model.PaymentAlertRule;
import com.oct.invoicesystem.domain.payment.repository.PaymentAlertRuleRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies that the payment due-date alert job (B4) is driven by the configured {@link
 * PaymentAlertRule} thresholds: an invoice due in N days is alerted only when an active J-N rule
 * exists.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentDueAlertJobTest {

    @Autowired private DeadlineReminderJob job;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentAlertRuleRepository ruleRepository;

    @MockBean private EmailService emailService;

    private User seedComptable() {
        Role role = roleRepository.findByName("ROLE_ASSISTANT_COMPTABLE").orElseGet(() -> {
            Role r = new Role(); r.setName("ROLE_ASSISTANT_COMPTABLE"); return roleRepository.save(r);
        });
        User u = userRepository.save(User.builder()
                .username("alert-aa").email("alert-aa@oct.test").password("$2a$12$dummy")
                .firstName("Alert").lastName("AA").active(true).preferredLang("fr").build());
        UserRole ur = UserRole.builder().id(new UserRoleId(u.getId(), role.getId())).user(u).role(role).build();
        u.getUserRoles().add(ur);
        return userRepository.save(u);
    }

    private Invoice seedPayableDueInDays(String ref, int days) {
        Department d = departmentRepository.findByCode("ALERT").orElseGet(() -> {
            Department dep = new Department();
            dep.setCode("ALERT"); dep.setNameFr("Alert"); dep.setNameEn("Alert");
            dep.setN1Role("ROLE_MANAGER"); dep.setActive(true); dep.setRequiresN2(false);
            return departmentRepository.save(dep);
        });
        User submitter = userRepository.save(User.builder()
                .username("alert-sub-" + ref).email("sub-" + ref + "@oct.test").password("$2a$12$dummy")
                .firstName("Sub").lastName("Mitter").active(true).preferredLang("fr").build());
        Invoice i = new Invoice();
        i.setReferenceNumber(ref);
        i.setSupplierName("Alert Supplier");
        i.setSupplierEmail("alert.supplier@oct.test");
        i.setAmount(new BigDecimal("900.00"));
        i.setCurrency("XAF");
        i.setIssueDate(LocalDate.now());
        i.setDueDate(LocalDate.now().plusDays(days));
        i.setDepartment(d);
        i.setSubmittedBy(submitter);
        i.setStatus(InvoiceStatus.BON_A_PAYER);
        return invoiceRepository.save(i);
    }

    @Test
    void alertsInvoiceMatchingActiveRule_andSkipsNonMatching() {
        seedComptable();
        ruleRepository.save(PaymentAlertRule.builder().daysBeforeDue(3).label("J-3").active(true).build());

        seedPayableDueInDays("FAC-ALERT-DUE3", 3);  // matches the J-3 rule → alerted
        seedPayableDueInDays("FAC-ALERT-DUE5", 5);  // no J-5 rule → not alerted

        job.sendPaymentDueAlerts();

        // Exactly one alert email sent (for the J-3 invoice) to the single comptable.
        verify(emailService, times(1)).sendEmail(
                eq("alert-aa@oct.test"), any(), eq("payment-due-alert"), any());
    }

    @Test
    void noActiveRule_fallsBackToSevenDayDefault() {
        seedComptable();
        // No rules configured at all → default 7-day threshold applies.
        seedPayableDueInDays("FAC-ALERT-DUE7", 7);
        seedPayableDueInDays("FAC-ALERT-DUE2", 2);

        job.sendPaymentDueAlerts();

        verify(emailService, times(1)).sendEmail(any(), any(), eq("payment-due-alert"), any());
    }
}
