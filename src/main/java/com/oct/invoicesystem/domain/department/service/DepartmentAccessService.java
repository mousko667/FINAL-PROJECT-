package com.oct.invoicesystem.domain.department.service;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;

import java.util.List;

public interface DepartmentAccessService {

    /** Retourne, par département (trié par code), les utilisateurs rattachés, leurs rôles
     *  et le niveau de validation. Lecture seule ; aucune donnée financière. */
    List<DepartmentAccessDTO> getDepartmentAccessOverview();
}
