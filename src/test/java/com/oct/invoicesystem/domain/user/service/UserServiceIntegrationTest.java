package com.oct.invoicesystem.domain.user.service;

import com.oct.invoicesystem.domain.user.dto.AssignRoleRequest;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for {@link UserService#assignRoles} against the real persistence layer.
 * The controller test mocks UserService, so it never exercises the JPA flush — this test does,
 * guarding the UserRoleId composite-key bug (PROB-040) where assignRoles built a UserRole without
 * setting its @EmbeddedId, causing a flush-time NPE / JpaSystemException.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private Role role(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(Role.builder().name(name).description(name).build()));
    }

    @Test
    void assignRoles_persistsRolesWithCompositeKey() {
        Role admin = role("ROLE_ADMIN");
        Role daf = role("ROLE_DAF");

        User user = userRepository.save(User.builder()
                .username("assign-it").email("assign-it@oct.test")
                .password("$2a$12$dummy").firstName("Assign").lastName("It")
                .active(true).preferredLang("fr").build());

        // This flushes the new UserRole join rows — it threw before the UserRoleId fix.
        userService.assignRoles(user.getId(), new AssignRoleRequest(List.of(admin.getId(), daf.getId())));

        User reloaded = userRepository.findByUsername("assign-it").orElseThrow();
        assertThat(reloaded.getUserRoles()).hasSize(2);
        assertThat(reloaded.getUserRoles())
                .allSatisfy(ur -> assertThat(ur.getId()).isNotNull());
        assertThat(reloaded.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_DAF");
    }

    @Test
    void assignRoles_replacesExistingRoles() {
        Role admin = role("ROLE_ADMIN");
        Role daf = role("ROLE_DAF");

        User user = userRepository.save(User.builder()
                .username("replace-it").email("replace-it@oct.test")
                .password("$2a$12$dummy").firstName("Replace").lastName("It")
                .active(true).preferredLang("fr").build());

        userService.assignRoles(user.getId(), new AssignRoleRequest(List.of(admin.getId())));
        userService.assignRoles(user.getId(), new AssignRoleRequest(List.of(daf.getId())));

        User reloaded = userRepository.findByUsername("replace-it").orElseThrow();
        assertThat(reloaded.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_DAF");
    }
}
