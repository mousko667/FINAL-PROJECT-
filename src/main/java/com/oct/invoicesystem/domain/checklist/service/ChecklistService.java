package com.oct.invoicesystem.domain.checklist.service;

import com.oct.invoicesystem.domain.checklist.dto.ChecklistResponseRequest;
import com.oct.invoicesystem.domain.checklist.dto.ChecklistTemplateDTO;
import com.oct.invoicesystem.domain.checklist.dto.ChecklistTemplateRequest;
import com.oct.invoicesystem.domain.checklist.dto.InvoiceChecklistDTO;
import com.oct.invoicesystem.domain.checklist.model.ChecklistResponse;
import com.oct.invoicesystem.domain.checklist.model.ChecklistResponseItem;
import com.oct.invoicesystem.domain.checklist.model.ChecklistTemplate;
import com.oct.invoicesystem.domain.checklist.model.ChecklistTemplateItem;
import com.oct.invoicesystem.domain.checklist.repository.ChecklistResponseRepository;
import com.oct.invoicesystem.domain.checklist.repository.ChecklistTemplateRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validation checklist templates (B1, M4). Admins manage reusable templates; validators see the
 * applicable template on the review screen and their answers are persisted per invoice (traced, but
 * non-blocking — there is no workflow guard tied to checklist completion).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChecklistService {

    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistResponseRepository responseRepository;
    private final InvoiceService invoiceService;

    // ── Admin: template CRUD ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChecklistTemplateDTO> listTemplates() {
        return templateRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ChecklistTemplateDTO getTemplate(UUID id) {
        return toDto(requireTemplate(id));
    }

    public ChecklistTemplateDTO createTemplate(ChecklistTemplateRequest request, User actor) {
        ChecklistTemplate template = ChecklistTemplate.builder()
                .name(request.name())
                .departmentId(request.departmentId())
                .active(request.active())
                .createdBy(actor)
                .build();
        applyItems(template, request);
        return toDto(templateRepository.save(template));
    }

    public ChecklistTemplateDTO updateTemplate(UUID id, ChecklistTemplateRequest request) {
        ChecklistTemplate template = requireTemplate(id);
        template.setName(request.name());
        template.setDepartmentId(request.departmentId());
        template.setActive(request.active());
        template.getItems().clear();
        applyItems(template, request);
        return toDto(templateRepository.save(template));
    }

    public void deleteTemplate(UUID id) {
        templateRepository.delete(requireTemplate(id));
    }

    // ── Validation side: resolve + answer ────────────────────────────────────

    /**
     * Returns the checklist for an invoice: the applicable template (department-scoped if available,
     * otherwise the active global one) merged with any answers already recorded. {@code templateId}
     * is null when no template applies.
     */
    @Transactional(readOnly = true)
    public InvoiceChecklistDTO getInvoiceChecklist(UUID invoiceId) {
        Invoice invoice = invoiceService.getById(invoiceId);
        UUID departmentId = invoice.getDepartment() != null ? invoice.getDepartment().getId() : null;
        ChecklistTemplate template = resolveTemplate(departmentId);
        if (template == null) {
            return new InvoiceChecklistDTO(null, null, false, null, null, List.of());
        }

        ChecklistResponse response = responseRepository
                .findFirstByInvoiceIdOrderByRespondedAtDesc(invoiceId).orElse(null);
        Map<UUID, ChecklistResponseItem> answers = response == null ? Map.of()
                : response.getItems().stream().collect(Collectors.toMap(
                        ChecklistResponseItem::getTemplateItemId, Function.identity(), (a, b) -> a));

        List<InvoiceChecklistDTO.ItemView> items = template.getItems().stream()
                .map(item -> {
                    ChecklistResponseItem ans = answers.get(item.getId());
                    return new InvoiceChecklistDTO.ItemView(
                            item.getId(), item.getLabel(), item.isRequired(), item.getDisplayOrder(),
                            ans != null && ans.isChecked(), ans != null ? ans.getNote() : null);
                })
                .toList();

        return new InvoiceChecklistDTO(
                template.getId(), template.getName(),
                response != null,
                response != null && response.getRespondedBy() != null ? response.getRespondedBy().getId() : null,
                response != null ? response.getRespondedAt() : null,
                items);
    }

    /**
     * Records (or replaces) the validator's answers for an invoice. The latest response is the one
     * read back by {@link #getInvoiceChecklist}; previous responses are kept for audit.
     */
    public InvoiceChecklistDTO saveResponse(UUID invoiceId, ChecklistResponseRequest request, User actor) {
        invoiceService.getById(invoiceId); // validates existence
        ChecklistTemplate template = requireTemplate(request.templateId());

        ChecklistResponse response = ChecklistResponse.builder()
                .invoiceId(invoiceId)
                .template(template)
                .respondedBy(actor)
                .build();
        for (ChecklistResponseRequest.ItemAnswer answer : request.items()) {
            response.getItems().add(ChecklistResponseItem.builder()
                    .response(response)
                    .templateItemId(answer.templateItemId())
                    .checked(answer.checked())
                    .note(answer.note())
                    .build());
        }
        responseRepository.save(response);
        return getInvoiceChecklist(invoiceId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ChecklistTemplate resolveTemplate(UUID departmentId) {
        if (departmentId != null) {
            List<ChecklistTemplate> applicable = templateRepository.findApplicable(departmentId);
            if (!applicable.isEmpty()) {
                return applicable.get(0);
            }
        }
        return templateRepository.findFirstByActiveTrueAndDepartmentIdIsNullOrderByCreatedAtDesc().orElse(null);
    }

    private ChecklistTemplate requireTemplate(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.checklist.template_not_found"));
    }

    private void applyItems(ChecklistTemplate template, ChecklistTemplateRequest request) {
        List<ChecklistTemplateItem> items = new ArrayList<>();
        for (ChecklistTemplateRequest.ItemRequest itemReq : request.items()) {
            items.add(ChecklistTemplateItem.builder()
                    .template(template)
                    .label(itemReq.label())
                    .required(itemReq.required())
                    .displayOrder(itemReq.displayOrder())
                    .build());
        }
        template.getItems().addAll(items);
    }

    private ChecklistTemplateDTO toDto(ChecklistTemplate t) {
        List<ChecklistTemplateDTO.ItemDTO> items = t.getItems().stream()
                .map(i -> new ChecklistTemplateDTO.ItemDTO(i.getId(), i.getLabel(), i.isRequired(), i.getDisplayOrder()))
                .toList();
        return new ChecklistTemplateDTO(t.getId(), t.getName(), t.getDepartmentId(), t.isActive(),
                items, t.getCreatedAt(), t.getUpdatedAt());
    }
}
