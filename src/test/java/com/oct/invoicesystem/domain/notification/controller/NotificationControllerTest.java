package com.oct.invoicesystem.domain.notification.controller;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.notification.model.Notification;
import com.oct.invoicesystem.domain.notification.model.NotificationType;
import com.oct.invoicesystem.domain.notification.repository.NotificationRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.task.execution.pool.core-size=1",
    "spring.task.execution.thread-name-prefix=test-async-"
})
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private InvoiceDocumentRepository invoiceDocumentRepository;
    @Autowired private InvoiceStateMachineService invoiceStateMachineService;
    @Autowired private ApprovalStepRepository approvalStepRepository;
    @Autowired private InvoiceStatusHistoryRepository invoiceStatusHistoryRepository;

    private User assistant;
    private User n1Drh;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        cleanDb();

        assistant = createUser("asst_notif", "ROLE_ASSISTANT_COMPTABLE");
        n1Drh = createUser("n1_notif_drh", "ROLE_VALIDATEUR_N1_DRH");

        Department dept = departmentRepository.findByCode("DRH")
                .orElseGet(() -> departmentRepository.save(Department.builder()
                        .code("DRH")
                        .nameFr("Direction Ressources Humaines")
                        .nameEn("Human Resources")
                        .requiresN2(false)
                        .n1Role("ROLE_VALIDATEUR_N1_DRH")
                        .isActive(true)
                        .build()));

        invoice = invoiceRepository.save(Invoice.builder()
                .referenceNumber("FAC-NOTIF-001")
                .department(dept)
                .submittedBy(assistant)
                .supplierName("TestSupplier")
                .supplierEmail("s@s.com")
                .amount(BigDecimal.valueOf(50_000))
                .currency("XAF")
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .status(InvoiceStatus.BROUILLON)
                .build());

        invoiceDocumentRepository.save(InvoiceDocument.builder()
                .invoice(invoice)
                .originalFilename("receipt.pdf")
                .minioObjectKey("notif-test/" + UUID.randomUUID())
                .fileType("application/pdf")
                .fileSizeBytes(1024L)
                .checksumSha256("abc123")
                .uploadedBy(assistant)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private void cleanDb() {
        notificationRepository.deleteAll();
        approvalStepRepository.deleteAll();
        invoiceStatusHistoryRepository.deleteAll();
        invoiceDocumentRepository.deleteAll();
        invoiceRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Verify that submitting an invoice via the state machine persists
     * a SUBMISSION notification for each N1 approver.
     * With single-threaded executor, the async listener completes before we query.
     */
    @Test
    void submitInvoice_persistsSubmissionNotificationForN1() throws Exception {
        invoiceStateMachineService.sendEvent(invoice.getId(), InvoiceEvent.SUBMIT,
                java.util.Map.of(WorkflowExtendedStateKeys.USER_ID, assistant.getId()));

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findByUserId(
                    n1Drh.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
            
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.SUBMISSION);
        });
    }

    @Test
    void getNotifications_returnsUserNotifications() throws Exception {
        notificationRepository.save(Notification.builder()
                .user(assistant)
                .invoice(invoice)
                .titleFr("Test").titleEn("Test")
                .messageFr("Msg FR").messageEn("Msg EN")
                .type(NotificationType.SUBMISSION)
                .isRead(false)
                .build());

        mockMvc.perform(get("/api/v1/notifications")
                .with(authentication(auth(assistant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].titleFr").value("Test"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void unreadCount_returns1_whenOneUnread() throws Exception {
        notificationRepository.save(Notification.builder()
                .user(assistant)
                .titleFr("T").titleEn("T").messageFr("M").messageEn("M")
                .type(NotificationType.SUBMISSION).build());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                .with(authentication(auth(assistant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void markAllAsRead_setsAllNotificationsRead() throws Exception {
        notificationRepository.save(Notification.builder()
                .user(assistant).invoice(invoice)
                .titleFr("T").titleEn("T").messageFr("M").messageEn("M")
                .type(NotificationType.SUBMISSION).isRead(false).build());

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .with(authentication(auth(assistant))))
                .andExpect(status().isOk());

        long unread = notificationRepository.countUnreadByUserId(assistant.getId());
        assertThat(unread).isZero();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private User createUser(String username, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(roleName).description(roleName).build()));

        User user = userRepository.save(User.builder()
                .username(username).email(username + "@oct.ga")
                .password("$2a$12$dummy").firstName("Test").lastName("User")
                .active(true).preferredLang("fr").build());

        UserRoleId id = new UserRoleId();
        id.setUserId(user.getId());
        id.setRoleId(role.getId());
        UserRole ur = UserRole.builder().id(id).user(user).role(role).build();
        user.getUserRoles().add(ur);
        userRepository.saveAndFlush(user);
        return user;
    }

    private UsernamePasswordAuthenticationToken auth(User user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
