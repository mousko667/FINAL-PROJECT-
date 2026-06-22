-- V5 — Seed the canonical role catalogue and the bootstrap admin user.
-- Exactly 6 role categories (briefing §5): ADMIN, SUPPLIER, ASSISTANT_COMPTABLE, DAF,
-- the 8 N1 department validators, and the 3 N2 department validators (INFO/INFRA/TECH).
-- No legacy roles (ACCOUNTANT/APPROVER/MANAGER/READONLY) and no phantom roles
-- (AUDITEUR, VALIDATEUR_N1_FIN) — those were removed during development and are
-- intentionally absent from this consolidated baseline.

INSERT INTO roles (name, description) VALUES
    ('ROLE_ADMIN',                 'Administrator with full access to users, roles, config and system/security audit'),
    ('ROLE_SUPPLIER',              'Supplier Portal User'),
    ('ROLE_ASSISTANT_COMPTABLE',   'Accounts payable clerk who creates and processes invoices'),
    ('ROLE_DAF',                   'Finance manager / Directeur Administratif et Financier (CFO)'),
    ('ROLE_VALIDATEUR_N1_DRH',     'N1 validator for DRH department'),
    ('ROLE_VALIDATEUR_N1_DG',      'N1 validator for DG department'),
    ('ROLE_VALIDATEUR_N1_INFO',    'N1 validator for INFO department'),
    ('ROLE_VALIDATEUR_N2_INFO',    'N2 validator for INFO department'),
    ('ROLE_VALIDATEUR_N1_TERM',    'N1 validator for TERM department'),
    ('ROLE_VALIDATEUR_N1_COM',     'N1 validator for COM department'),
    ('ROLE_VALIDATEUR_N1_QHSSE',   'N1 validator for QHSSE department'),
    ('ROLE_VALIDATEUR_N1_INFRA',   'N1 validator for INFRA department'),
    ('ROLE_VALIDATEUR_N2_INFRA',   'N2 validator for INFRA department'),
    ('ROLE_VALIDATEUR_N1_TECH',    'N1 validator for TECH department'),
    ('ROLE_VALIDATEUR_N2_TECH',    'N2 validator for TECH department')
ON CONFLICT (name) DO NOTHING;

-- Bootstrap administrator (password hash preserved from the original V3 seed).
INSERT INTO users (username, password_hash, email, first_name, last_name, preferred_lang, is_active)
VALUES (
    'admin',
    '$2a$12$0qFymNId2uaV35x6w/qOrebLgapckwvPdU.pgLu3t.ThFhI52s406',
    'admin@oct.com',
    'System',
    'Admin',
    'EN',
    true
)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;
