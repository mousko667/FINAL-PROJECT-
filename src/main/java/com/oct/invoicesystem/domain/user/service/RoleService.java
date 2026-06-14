package com.oct.invoicesystem.domain.user.service;

import com.oct.invoicesystem.domain.user.dto.RoleDTO;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Read access to the role catalogue. Backs the permission-matrix editor (P11-18), which needs the
 * role name→UUID mapping to call {@code PUT /users/{id}/roles} (that endpoint takes role UUIDs).
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    /**
     * Returns every role in the catalogue, sorted by name. The frontend decides which roles to
     * surface (e.g. the matrix only renders the canonical assignable roles); this endpoint exists
     * purely to resolve role names to their UUIDs.
     */
    @Transactional(readOnly = true)
    public List<RoleDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(r -> new RoleDTO(r.getId(), r.getName(), r.getDescription()))
                .toList();
    }
}
