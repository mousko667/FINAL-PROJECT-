package com.oct.invoicesystem.domain.department.service;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentAccessServiceTest {

    @Mock DepartmentRepository departmentRepository;
    @Mock UserRepository userRepository;
    @InjectMocks DepartmentAccessServiceImpl service;

    private Department dept(UUID id, String code, boolean requiresN2) {
        return Department.builder().id(id).code(code).nameFr(code + " FR").nameEn(code + " EN")
                .requiresN2(requiresN2).n1Role("ROLE_N1").n2Role(requiresN2 ? "ROLE_N2" : null).build();
    }

    private User user(UUID deptId, String username, boolean active, String roleName) {
        Role role = Role.builder().name(roleName).build();
        User u = User.builder().id(UUID.randomUUID()).username(username).firstName("F").lastName("L")
                .departmentId(deptId).active(active).build();
        u.setUserRoles(Set.of(UserRole.builder().user(u).role(role).build()));
        return u;
    }

    @Test
    void overview_groupsUsersByDepartment_andCountsActive() {
        UUID d1 = UUID.randomUUID();
        Department dep = dept(d1, "IT", true);
        when(departmentRepository.findAll()).thenReturn(List.of(dep));
        when(userRepository.findByDepartmentIdIn(anyCollection()))
                .thenReturn(List.of(user(d1, "alice", true, "ROLE_N1"), user(d1, "bob", false, "ROLE_N2")));

        List<DepartmentAccessDTO> result = service.getDepartmentAccessOverview();

        assertThat(result).hasSize(1);
        DepartmentAccessDTO it = result.get(0);
        assertThat(it.code()).isEqualTo("IT");
        assertThat(it.requiresN2()).isTrue();
        assertThat(it.userCount()).isEqualTo(2);
        assertThat(it.activeCount()).isEqualTo(1);
        assertThat(it.users()).extracting("username").containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void overview_departmentWithoutUsers_hasZeroCounts() {
        UUID d1 = UUID.randomUUID();
        when(departmentRepository.findAll()).thenReturn(List.of(dept(d1, "RH", false)));
        when(userRepository.findByDepartmentIdIn(anyCollection())).thenReturn(List.of());

        List<DepartmentAccessDTO> result = service.getDepartmentAccessOverview();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userCount()).isZero();
        assertThat(result.get(0).activeCount()).isZero();
        assertThat(result.get(0).users()).isEmpty();
    }

    @Test
    void overview_mapsRoleNames() {
        UUID d1 = UUID.randomUUID();
        when(departmentRepository.findAll()).thenReturn(List.of(dept(d1, "IT", false)));
        when(userRepository.findByDepartmentIdIn(anyCollection()))
                .thenReturn(List.of(user(d1, "alice", true, "ROLE_VALIDATEUR_N1_IT")));

        List<DepartmentAccessDTO> result = service.getDepartmentAccessOverview();

        assertThat(result.get(0).users().get(0).roles()).containsExactly("ROLE_VALIDATEUR_N1_IT");
    }
}
