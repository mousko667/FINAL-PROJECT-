-- V31: Fix Finance department approver and remove phantom roles
-- Per briefing §5.5 and §6: The CFO (ROLE_DAF) is the Level 1 approver for the Finance department.
-- ROLE_VALIDATEUR_N1_FIN was incorrectly seeded — it duplicates ROLE_DAF's function.
-- ROLE_AUDITEUR does not exist in the system (briefing §5: exactly 6 roles).

-- Fix Finance department to use ROLE_DAF as the N1 approver
UPDATE departments
SET n1_role = 'ROLE_DAF'
WHERE code = 'FIN';

-- Remove phantom roles that do not belong in the system
DELETE FROM roles WHERE name IN ('ROLE_AUDITEUR', 'ROLE_VALIDATEUR_N1_FIN');
