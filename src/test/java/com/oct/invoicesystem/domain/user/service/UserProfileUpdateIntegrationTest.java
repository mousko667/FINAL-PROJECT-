package com.oct.invoicesystem.domain.user.service;

import com.oct.invoicesystem.domain.user.dto.AssignRoleRequest;
import com.oct.invoicesystem.domain.user.dto.UserDTO;
import com.oct.invoicesystem.domain.user.mapper.UserMapper;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards PROB-080: {@link UserService#updateProfile} returned an entity whose LAZY
 * {@code userRoles} collection was uninitialised. The controller then mapped it to a DTO
 * outside the transaction (UserProfileController.updateProfile), throwing
 * {@link org.hibernate.LazyInitializationException} → HTTP 500 on PUT /api/v1/profile.
 *
 * <p>This test is intentionally NOT {@code @Transactional}: a test-managed transaction would
 * keep the Hibernate session open and hide the bug (exactly how it slipped through). Mapping
 * the returned entity after the service transaction has committed reproduces the production path.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserProfileUpdateIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserMapper userMapper;

    private Role role(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(Role.builder().name(name).description(name).build()));
    }

    @Test
    void updateProfile_returnsEntityMappableOutsideTransaction() {
        Role daf = role("ROLE_DAF");
        User user = userRepository.save(User.builder()
                .username("profile-it").email("profile-it@oct.test")
                .password("$2a$12$dummy").firstName("Pro").lastName("File")
                .active(true).preferredLang("fr").build());
        userService.assignRoles(user.getId(), new AssignRoleRequest(List.of(daf.getId())));

        // Service transaction commits here; the returned entity is now detached.
        User saved = userService.updateProfile(user.getId(), "Updated", "Name", "en");

        // Mapping accesses the LAZY userRoles collection — threw LazyInitializationException before the fix.
        UserDTO[] dto = new UserDTO[1];
        assertThatCode(() -> dto[0] = userMapper.toDto(saved)).doesNotThrowAnyException();
        assertThat(dto[0].firstName()).isEqualTo("Updated");
        assertThat(dto[0].roles()).containsExactly("ROLE_DAF");
    }
}
