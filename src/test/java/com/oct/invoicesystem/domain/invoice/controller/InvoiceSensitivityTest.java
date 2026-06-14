package com.oct.invoicesystem.domain.invoice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.dto.UpdateSensitivityRequest;
import com.oct.invoicesystem.domain.invoice.model.DataSensitivity;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration coverage for the data-sensitivity classification (P11-15): default value,
 * the PATCH endpoint, and its role restriction (DAF + ASSISTANT_COMPTABLE only).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvoiceSensitivityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    private User assistant;
    private User n1Drh;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        Department dept = departmentRepository.findByCode("DRH")
                .orElseGet(() -> departmentRepository.save(Department.builder()
                        .code("DRH").nameFr("RH").nameEn("HR").requiresN2(false)
                        .n1Role("ROLE_VALIDATEUR_N1_DRH").isActive(true).build()));

        assistant = createUser("sens_asst", "ROLE_ASSISTANT_COMPTABLE");
        n1Drh = createUser("sens_n1", "ROLE_VALIDATEUR_N1_DRH");

        invoice = invoiceRepository.save(Invoice.builder()
                .referenceNumber("FAC-SENS-001").department(dept).submittedBy(assistant)
                .supplierName("S").supplierEmail("s@s.com").amount(BigDecimal.valueOf(1000))
                .currency("XAF").issueDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .status(InvoiceStatus.BROUILLON).build());
    }

    @Test
    void newInvoice_defaultsToInternal() {
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getDataSensitivity())
                .isEqualTo(DataSensitivity.INTERNAL);
    }

    @Test
    void patchSensitivity_asAssistant_updatesAndPersists() throws Exception {
        mockMvc.perform(patch("/api/v1/invoices/{id}/sensitivity", invoice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSensitivityRequest(DataSensitivity.CONFIDENTIAL)))
                        .with(authentication(auth(assistant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataSensitivity").value("CONFIDENTIAL"));

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getDataSensitivity())
                .isEqualTo(DataSensitivity.CONFIDENTIAL);
    }

    @Test
    void patchSensitivity_asUnauthorizedRole_isForbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/invoices/{id}/sensitivity", invoice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSensitivityRequest(DataSensitivity.PUBLIC)))
                        .with(authentication(auth(n1Drh))))
                .andExpect(status().isForbidden());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User createUser(String username, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).description(roleName).build()));
        User user = userRepository.save(User.builder()
                .username(username).email(username + "@oct.test")
                .password("$2a$12$dummy").firstName("Test").lastName("User")
                .active(true).preferredLang("fr").mfaEnabled(true).mfaVerified(true).build());
        UserRole ur = UserRole.builder()
                .id(new UserRoleId(user.getId(), role.getId())).user(user).role(role).build();
        user.getUserRoles().add(ur);
        return userRepository.saveAndFlush(user);
    }

    private UsernamePasswordAuthenticationToken auth(User u) {
        return new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
    }
}
