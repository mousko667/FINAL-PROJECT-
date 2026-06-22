-- V1 — Departments (consolidated)
-- The 9 OCT departments and their N1/N2 approver roles. FIN's N1 approver is the CFO
-- (ROLE_DAF) by design. INFO / INFRA / TECH require a second approval level (N2).

CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(20) NOT NULL UNIQUE,
    name_fr     VARCHAR(255) NOT NULL,
    name_en     VARCHAR(255) NOT NULL,
    requires_n2 BOOLEAN NOT NULL DEFAULT FALSE,
    n1_role     VARCHAR(100) NOT NULL,
    n2_role     VARCHAR(100),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    budget      NUMERIC(15,2)
);

INSERT INTO departments (code, name_fr, name_en, requires_n2, n1_role, n2_role) VALUES
('DRH',   'Direction des Ressources Humaines', 'Human Resources Department', FALSE, 'ROLE_VALIDATEUR_N1_DRH',  NULL),
('DG',    'Direction Générale',                'General Management',         FALSE, 'ROLE_VALIDATEUR_N1_DG',   NULL),
('FIN',   'Finance',                           'Finance',                    FALSE, 'ROLE_DAF',                NULL),
('INFO',  'Informatique',                      'Information Technology',     TRUE,  'ROLE_VALIDATEUR_N1_INFO', 'ROLE_VALIDATEUR_N2_INFO'),
('TERM',  'Terminal',                          'Terminal',                   FALSE, 'ROLE_VALIDATEUR_N1_TERM', NULL),
('COM',   'Communication & RSE',               'Communication & CSR',        FALSE, 'ROLE_VALIDATEUR_N1_COM',  NULL),
('QHSSE', 'QHSSE',                             'QHSSE',                      FALSE, 'ROLE_VALIDATEUR_N1_QHSSE',NULL),
('INFRA', 'Infrastructure',                    'Infrastructure',             TRUE,  'ROLE_VALIDATEUR_N1_INFRA','ROLE_VALIDATEUR_N2_INFRA'),
('TECH',  'Atelier / Direction Technique',     'Technical Department',       TRUE,  'ROLE_VALIDATEUR_N1_TECH', 'ROLE_VALIDATEUR_N2_TECH');
