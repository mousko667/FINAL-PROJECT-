package com.oct.invoicesystem.domain.department.dto;

import java.util.List;
import java.util.UUID;

/** Aperçu lecture seule des accès d'un département : utilisateurs rattachés,
 *  rôles et niveau de validation. Aucune donnée financière (pas de budget). */
public record DepartmentAccessDTO(
        UUID departmentId,
        String code,
        String nameFr,
        String nameEn,
        boolean requiresN2,
        String n1Role,
        String n2Role,
        int userCount,
        int activeCount,
        List<DepartmentUserDTO> users
) {}
