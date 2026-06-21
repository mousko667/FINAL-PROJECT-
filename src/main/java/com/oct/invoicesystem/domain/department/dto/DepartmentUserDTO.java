package com.oct.invoicesystem.domain.department.dto;

import java.util.List;
import java.util.UUID;

/** Vue lecture seule d'un utilisateur dans l'aperçu des accès par département.
 *  Ne contient aucune donnée financière (pas d'approvalLimit). */
public record DepartmentUserDTO(
        UUID userId,
        String fullName,
        String username,
        boolean active,
        List<String> roles
) {}
