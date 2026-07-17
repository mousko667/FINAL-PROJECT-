package com.oct.invoicesystem.domain.supplier.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.auth.dto.LoginRequest;
import com.oct.invoicesystem.domain.auth.dto.SupplierRegistrationRequest;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceCreateRequest;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.supplier.model.SupplierDocumentType;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SupplierPortalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.oct.invoicesystem.domain.storage.service.MinioStorageService minioStorageService;

    @BeforeEach
    void setUp() {
        if (roleRepository.findByName("ROLE_SUPPLIER").isEmpty()) {
            roleRepository.save(Role.builder()
                    .name("ROLE_SUPPLIER")
                    .description("Supplier Portal User")
                    .build());
        }
    }

    @Test
    void fullSupplierFlow_IntegrationTest() throws Exception {
        // Create a department with all required fields
        Department dept = departmentRepository.save(Department.builder()
                .nameFr("Département Test")
                .nameEn("Test Department")
                .code("TDEPT-" + UUID.randomUUID().toString().substring(0, 8))
                .requiresN2(false)
                .n1Role("ROLE_RESPONSABLE_INFO") // Example role
                .isActive(true)
                .build());

        String email = "test-supplier-" + UUID.randomUUID() + "@example.com";
        String password = "SecurePassword123!";

        // 1. Registration
        SupplierRegistrationRequest regReq = new SupplierRegistrationRequest(
                "Test Supplier Co",
                "TAX-" + UUID.randomUUID(),
                email,
                password,
                "Supplier",
                "User",
                "+123456789",
                "BANK-DETAILS-X",
                "123 Supplier Lane"
        );

        mockMvc.perform(post("/api/v1/auth/register/supplier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andDo(print())
                .andExpect(status().isOk());

        // 2. Email Verification (Extract token from DB)
        User user = userRepository.findByEmail(email).orElseThrow();
        String token = user.getEmailVerificationToken();
        assertThat(token).isNotNull();

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", token))
                .andDo(print())
                .andExpect(status().isOk());

        // 3. Login
        LoginRequest loginReq = new LoginRequest(email, password);
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn().getResponse().getContentAsString();

        String jwt = objectMapper.readTree(loginResponse).get("data").get("accessToken").asText();
        String authHeader = "Bearer " + jwt;

        // 4. Submit Invoice
        InvoiceCreateRequest invoiceReq = new InvoiceCreateRequest(
                dept.getId(),
                null, // supplierId will be overwritten by service
                null, null, null, null, // supplier info fields
                BigDecimal.valueOf(1500.50),
                "EUR",
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                "Service delivery for March"
        );

        String submitResponse = mockMvc.perform(post("/api/v1/supplier/invoices")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceReq)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value(InvoiceStatus.BROUILLON.name()))
                .andExpect(jsonPath("$.data.amount").value(1500.50))
                .andReturn().getResponse().getContentAsString();

        String referenceNumber = objectMapper.readTree(submitResponse).get("data").get("referenceNumber").asText();

        // 5. Track Status (List Invoices)
        mockMvc.perform(get("/api/v1/supplier/invoices")
                        .header("Authorization", authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 6. Upload Document
        MockMultipartFile file = new MockMultipartFile(
                "file", "tax_cert.pdf", "application/pdf", "dummy content".getBytes());
        
        mockMvc.perform(multipart("/api/v1/supplier/documents")
                        .file(file)
                        .param("documentType", SupplierDocumentType.TAX_CERTIFICATE.name())
                        .header("Authorization", authHeader))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.filename").value("tax_cert.pdf"));

        // 7. Get Dashboard Stats - an invoice under AA control must still count as pending
        Invoice invoice = invoiceRepository.findByReferenceNumber(referenceNumber).orElseThrow();
        invoice.setStatus(InvoiceStatus.EN_CONTROLE_AA);
        invoiceRepository.save(invoice);

        mockMvc.perform(get("/api/v1/supplier/dashboard")
                        .header("Authorization", authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCounts").exists())
                .andExpect(jsonPath("$.data.matchingStatusBreakdown").exists())
                .andExpect(jsonPath("$.data.pendingCount").value(1));

        // 8. Get Profile
        mockMvc.perform(get("/api/v1/supplier/profile")
                        .header("Authorization", authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("Test Supplier Co"))
                .andExpect(jsonPath("$.data.bankDetails").doesNotExist())
                .andExpect(jsonPath("$.data.bank_details").doesNotExist());
    }

    @Test
    void expiredToken_ReturnsError() throws Exception {
        String email = "expired@example.com";
        SupplierRegistrationRequest regReq = new SupplierRegistrationRequest(
                "Expired Corp", "TAX-EXP", email, "Pass123!", "Exp", "Usr", "+1", "Bank", "Addr"
        );
        mockMvc.perform(post("/api/v1/auth/register/supplier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerificationTokenExpiry(java.time.Instant.now().minusSeconds(3600)); // 1h ago
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", user.getEmailVerificationToken()))
                .andExpect(status().isBadRequest()) // Now throws ValidationException -> 400
                .andExpect(jsonPath("$.message").value("Verification token has expired"));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "SUPPLIER")
    void supplier_CannotAccessStaffEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void accountant_CannotAccessSupplierPortal() throws Exception {
        mockMvc.perform(get("/api/v1/supplier/invoices"))
                .andExpect(status().isForbidden());
    }
}
