-- V33: Remove phantom roles that are not part of the system design
-- Valid roles: ROLE_SUPPLIER, ROLE_ASSISTANT_COMPTABLE, ROLE_DAF, ROLE_ADMIN,
--              ROLE_VALIDATEUR_N1_{DRH,DG,INFO,TERM,COM,QHSSE,INFRA,TECH},
--              ROLE_VALIDATEUR_N2_{INFO,INFRA,TECH}

-- Remove user_role assignments for phantom roles first (FK constraint)
DELETE FROM user_roles
WHERE role_id IN (
    SELECT id FROM roles
    WHERE name IN ('ROLE_ACCOUNTANT', 'ROLE_APPROVER', 'ROLE_MANAGER', 'ROLE_READONLY')
);

-- Delete the phantom roles
DELETE FROM roles
WHERE name IN ('ROLE_ACCOUNTANT', 'ROLE_APPROVER', 'ROLE_MANAGER', 'ROLE_READONLY');
