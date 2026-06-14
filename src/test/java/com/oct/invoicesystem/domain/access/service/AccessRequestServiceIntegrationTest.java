package com.oct.invoicesystem.domain.access.service;

import com.oct.invoicesystem.domain.access.dto.AccessRequestCreateRequest;
import com.oct.invoicesystem.domain.access.dto.AccessRequestDTO;
import com.oct.invoicesystem.domain.access.dto.AccessRequestReviewRequest;
import com.oct.invoicesystem.domain.access.model.AccessRequestStatus;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration coverage for {@link AccessRequestService} against the real persistence layer
 * (P11-17 / REQ-23 item 3). Exercises the JPA flush of the role-add on approval, which the
 * mocked controller test never reaches.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccessRequestServiceIntegrationTest {

    @Autowired private AccessRequestService accessRequestService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private Role role(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(Role.builder().name(name).description(name).build()));
    }

    private User staff(String username) {
        return userRepository.save(User.builder()
                .username(username).email(username + "@oct.test")
                .password("$2a$12$dummy").firstName("Staff").lastName("User")
                .active(true).preferredLang("fr").build());
    }

    @Test
    void create_persistsPendingRequest() {
        User requester = staff("ar-create");
        role("ROLE_DAF");

        AccessRequestDTO dto = accessRequestService.create(
                requester.getId(), new AccessRequestCreateRequest("ROLE_DAF", "I manage finance"));

        assertThat(dto.status()).isEqualTo(AccessRequestStatus.PENDING);
        assertThat(dto.requestedRole()).isEqualTo("ROLE_DAF");
        assertThat(dto.requesterId()).isEqualTo(requester.getId());
        assertThat(dto.reason()).isEqualTo("I manage finance");
    }

    @Test
    void create_unknownRole_throwsValidation() {
        User requester = staff("ar-badrole");

        assertThatThrownBy(() -> accessRequestService.create(
                requester.getId(), new AccessRequestCreateRequest("ROLE_DOES_NOT_EXIST", "x")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_duplicatePending_throwsValidation() {
        User requester = staff("ar-dup");
        role("ROLE_DAF");
        accessRequestService.create(requester.getId(), new AccessRequestCreateRequest("ROLE_DAF", "first"));

        assertThatThrownBy(() -> accessRequestService.create(
                requester.getId(), new AccessRequestCreateRequest("ROLE_DAF", "second")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void approve_addsRequestedRoleToRequester() {
        User requester = staff("ar-approve");
        User admin = staff("ar-admin");
        role("ROLE_DAF");
        AccessRequestDTO created = accessRequestService.create(
                requester.getId(), new AccessRequestCreateRequest("ROLE_DAF", "need it"));

        AccessRequestDTO reviewed = accessRequestService.review(
                created.id(), admin.getId(), new AccessRequestReviewRequest(true, "approved"));

        assertThat(reviewed.status()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(reviewed.reviewedById()).isEqualTo(admin.getId());

        // The role must actually be granted to the requester (flush-time composite key path).
        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getAuthorities())
                .extracting("authority")
                .contains("ROLE_DAF");
    }

    @Test
    void reject_doesNotGrantRole() {
        User requester = staff("ar-reject");
        User admin = staff("ar-admin2");
        role("ROLE_DAF");
        AccessRequestDTO created = accessRequestService.create(
                requester.getId(), new AccessRequestCreateRequest("ROLE_DAF", "need it"));

        AccessRequestDTO reviewed = accessRequestService.review(
                created.id(), admin.getId(), new AccessRequestReviewRequest(false, "denied"));

        assertThat(reviewed.status()).isEqualTo(AccessRequestStatus.REJECTED);
        assertThat(reviewed.reviewComment()).isEqualTo("denied");

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getAuthorities())
                .extracting("authority")
                .doesNotContain("ROLE_DAF");
    }

    @Test
    void review_alreadyReviewed_throwsValidation() {
        User requester = staff("ar-twice");
        User admin = staff("ar-admin3");
        role("ROLE_DAF");
        AccessRequestDTO created = accessRequestService.create(
                requester.getId(), new AccessRequestCreateRequest("ROLE_DAF", "need it"));
        accessRequestService.review(created.id(), admin.getId(), new AccessRequestReviewRequest(true, null));

        assertThatThrownBy(() -> accessRequestService.review(
                created.id(), admin.getId(), new AccessRequestReviewRequest(false, "too late")))
                .isInstanceOf(ValidationException.class);
    }
}
