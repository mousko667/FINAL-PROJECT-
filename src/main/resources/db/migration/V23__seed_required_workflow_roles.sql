-- Seed production workflow roles required by the OCT BAP workflow.
-- Earlier migrations kept some legacy role names for compatibility; these are
-- the canonical roles used by controllers, departments, and MFA enforcement.

INSERT INTO roles (name, description) VALUES
    ('ROLE_ASSISTANT_COMPTABLE', 'Accounts payable clerk who creates and processes invoices'),
    ('ROLE_DAF', 'Finance manager / Directeur Administratif et Financier'),
    ('ROLE_AUDITEUR', 'Auditor with read-only audit and reporting access'),
    ('ROLE_VALIDATEUR_N1_DRH', 'N1 validator for DRH department'),
    ('ROLE_VALIDATEUR_N1_DG', 'N1 validator for DG department'),
    ('ROLE_VALIDATEUR_N1_FIN', 'N1 validator for FIN department'),
    ('ROLE_VALIDATEUR_N1_INFO', 'N1 validator for INFO department'),
    ('ROLE_VALIDATEUR_N2_INFO', 'N2 validator for INFO department'),
    ('ROLE_VALIDATEUR_N1_TERM', 'N1 validator for TERM department'),
    ('ROLE_VALIDATEUR_N1_COM', 'N1 validator for COM department'),
    ('ROLE_VALIDATEUR_N1_QHSSE', 'N1 validator for QHSSE department'),
    ('ROLE_VALIDATEUR_N1_INFRA', 'N1 validator for INFRA department'),
    ('ROLE_VALIDATEUR_N2_INFRA', 'N2 validator for INFRA department'),
    ('ROLE_VALIDATEUR_N1_TECH', 'N1 validator for TECH department'),
    ('ROLE_VALIDATEUR_N2_TECH', 'N2 validator for TECH department')
ON CONFLICT (name) DO NOTHING;
