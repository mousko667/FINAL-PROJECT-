package com.oct.invoicesystem.domain.checklist.service;

import com.oct.invoicesystem.domain.checklist.dto.ChecklistResponseRequest;
import com.oct.invoicesystem.domain.checklist.dto.ChecklistTemplateDTO;
import com.oct.invoicesystem.domain.checklist.dto.ChecklistTemplateRequest;
import com.oct.invoicesystem.domain.checklist.dto.InvoiceChecklistDTO;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for {@link ChecklistService} (B1) against the real persistence layer:
 * template CRUD, department-scoped template resolution for an invoice, and the persisted
 * round-trip of a validator's answers.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChecklistServiceIntegrationTest {

    @Autowired private ChecklistService checklistService;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    private Department department;
    private User actor;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        Department d = new Department();
        d.setCode("CHK");
        d.setNameFr("Checklist Dept");
        d.setNameEn("Checklist Dept");
        d.setN1Role("ROLE_MANAGER");
        d.setActive(true);
        d.setRequiresN2(false);
        department = departmentRepository.save(d);

        actor = userRepository.save(User.builder()
                .username("chk-actor").email("chk-actor@oct.test")
                .password("$2a$12$dummy").firstName("Chk").lastName("Actor")
                .active(true).preferredLang("fr").build());

        invoice = new Invoice();
        invoice.setReferenceNumber("FAC-CHK-001");
        invoice.setSupplierName("Supplier Chk");
        invoice.setSupplierEmail("supplier.chk@oct.test");
        invoice.setAmount(new BigDecimal("1000.00"));
        invoice.setCurrency("XAF");
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setDepartment(department);
        invoice.setSubmittedBy(actor);
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        invoice = invoiceRepository.save(invoice);
    }

    @Test
    void createTemplate_persistsOrderedItems() {
        ChecklistTemplateRequest request = new ChecklistTemplateRequest(
                "Standard checks", department.getId(), true,
                List.of(
                        new ChecklistTemplateRequest.ItemRequest("Amount matches PO", true, 0),
                        new ChecklistTemplateRequest.ItemRequest("Supplier verified", false, 1)));

        ChecklistTemplateDTO created = checklistService.createTemplate(request, actor);

        assertThat(created.id()).isNotNull();
        assertThat(created.items()).hasSize(2);
        assertThat(created.items().get(0).label()).isEqualTo("Amount matches PO");
        assertThat(created.items().get(0).required()).isTrue();
        assertThat(checklistService.listTemplates()).extracting(ChecklistTemplateDTO::name).contains("Standard checks");
    }

    @Test
    void invoiceChecklist_resolvesDepartmentTemplate_andRoundTripsAnswers() {
        ChecklistTemplateDTO template = checklistService.createTemplate(new ChecklistTemplateRequest(
                "Dept checks", department.getId(), true,
                List.of(new ChecklistTemplateRequest.ItemRequest("Check A", true, 0),
                        new ChecklistTemplateRequest.ItemRequest("Check B", false, 1))), actor);

        // Resolves the department template; no answers yet.
        InvoiceChecklistDTO before = checklistService.getInvoiceChecklist(invoice.getId());
        assertThat(before.templateId()).isEqualTo(template.id());
        assertThat(before.answered()).isFalse();
        assertThat(before.items()).hasSize(2).allSatisfy(i -> assertThat(i.checked()).isFalse());

        // Save answers: first item checked with a note, second unchecked.
        var firstItemId = template.items().get(0).id();
        var secondItemId = template.items().get(1).id();
        checklistService.saveResponse(invoice.getId(), new ChecklistResponseRequest(template.id(),
                List.of(new ChecklistResponseRequest.ItemAnswer(firstItemId, true, "verified against PO"),
                        new ChecklistResponseRequest.ItemAnswer(secondItemId, false, null))), actor);

        InvoiceChecklistDTO after = checklistService.getInvoiceChecklist(invoice.getId());
        assertThat(after.answered()).isTrue();
        assertThat(after.respondedBy()).isEqualTo(actor.getId());
        assertThat(after.items()).filteredOn(i -> i.templateItemId().equals(firstItemId))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.checked()).isTrue();
                    assertThat(i.note()).isEqualTo("verified against PO");
                });
    }

    @Test
    void invoiceChecklist_fallsBackToGlobalTemplate_whenNoDepartmentTemplate() {
        checklistService.createTemplate(new ChecklistTemplateRequest(
                "Global checks", null, true,
                List.of(new ChecklistTemplateRequest.ItemRequest("Global item", false, 0))), actor);

        InvoiceChecklistDTO checklist = checklistService.getInvoiceChecklist(invoice.getId());
        assertThat(checklist.templateName()).isEqualTo("Global checks");
        assertThat(checklist.items()).hasSize(1);
    }
}
