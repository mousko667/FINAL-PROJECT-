package com.oct.invoicesystem.domain.checklist.repository;

import com.oct.invoicesystem.domain.checklist.model.ChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {

    List<ChecklistTemplate> findAllByOrderByCreatedAtDesc();

    /**
     * Resolves the checklist template applicable to a department: the active template scoped to that
     * department if one exists, otherwise the active global template (department_id IS NULL). The
     * department parameter is CAST so PostgreSQL can determine its type when null
     * (PROB-038 / PROB-054 family). Most-recently-created wins when several are active.
     */
    @Query("""
            SELECT t FROM ChecklistTemplate t
            WHERE t.active = true
              AND (t.departmentId = :departmentId OR t.departmentId IS NULL)
            ORDER BY CASE WHEN t.departmentId = :departmentId THEN 0 ELSE 1 END, t.createdAt DESC
            """)
    List<ChecklistTemplate> findApplicable(@Param("departmentId") UUID departmentId);

    Optional<ChecklistTemplate> findFirstByActiveTrueAndDepartmentIdIsNullOrderByCreatedAtDesc();
}
