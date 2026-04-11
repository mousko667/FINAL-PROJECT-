-- E2E Playwright test seed for oct_invoice_dev (Docker postgres)

-- Insert E2E-specific roles
INSERT INTO roles (id, name, description, created_at)
SELECT gen_random_uuid(), v.name, v.name, NOW()
FROM (VALUES
  ('ROLE_ASSISTANT_COMPTABLE'),
  ('ROLE_VALIDATEUR_N1_DRH'),
  ('ROLE_VALIDATEUR_N1_INFO'),
  ('ROLE_VALIDATEUR_N2_INFO'),
  ('ROLE_DAF'),
  ('ROLE_AUDITEUR')
) AS v(name)
ON CONFLICT (name) DO NOTHING;

-- Insert E2E test users
-- Password: password123 -> verified BCrypt hash (strength 12)
-- hash: $2b$12$gEnIVDzIw4neKb06t0UqJe4zp9.8ptQUZ67xN6QRRUB.j3FM0W5CO
INSERT INTO users (id, username, password_hash, email, first_name, last_name, preferred_lang, is_active, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'e2e_assistant', '$2b$12$gEnIVDzIw4neKb06t0UqJe4zp9.8ptQUZ67xN6QRRUB.j3FM0W5CO', 'e2e_assistant@oct.test', 'E2E', 'Assistant', 'fr', true, NOW(), NOW()),
  (gen_random_uuid(), 'e2e_n1_drh',   '$2b$12$gEnIVDzIw4neKb06t0UqJe4zp9.8ptQUZ67xN6QRRUB.j3FM0W5CO', 'e2e_n1_drh@oct.test',   'E2E', 'N1DRH',     'fr', true, NOW(), NOW()),
  (gen_random_uuid(), 'e2e_daf',      '$2b$12$gEnIVDzIw4neKb06t0UqJe4zp9.8ptQUZ67xN6QRRUB.j3FM0W5CO', 'e2e_daf@oct.test',      'E2E', 'DAF',       'fr', true, NOW(), NOW()),
  (gen_random_uuid(), 'e2e_n1_info',  '$2b$12$gEnIVDzIw4neKb06t0UqJe4zp9.8ptQUZ67xN6QRRUB.j3FM0W5CO', 'e2e_n1_info@oct.test',  'E2E', 'N1Info',    'fr', true, NOW(), NOW()),
  (gen_random_uuid(), 'e2e_n2_info',  '$2b$12$gEnIVDzIw4neKb06t0UqJe4zp9.8ptQUZ67xN6QRRUB.j3FM0W5CO', 'e2e_n2_info@oct.test',  'E2E', 'N2Info',    'fr', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash;

-- Link roles to users
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE (u.username = 'e2e_assistant' AND r.name = 'ROLE_ASSISTANT_COMPTABLE')
   OR (u.username = 'e2e_n1_drh'   AND r.name = 'ROLE_VALIDATEUR_N1_DRH')
   OR (u.username = 'e2e_daf'      AND r.name = 'ROLE_DAF')
   OR (u.username = 'e2e_n1_info'  AND r.name = 'ROLE_VALIDATEUR_N1_INFO')
   OR (u.username = 'e2e_n2_info'  AND r.name = 'ROLE_VALIDATEUR_N2_INFO')
ON CONFLICT DO NOTHING;

-- Ensure DRH department exists (single-level)
INSERT INTO departments (id, code, name_fr, name_en, requires_n2, n1_role, n2_role, is_active, created_at, updated_at)
VALUES (gen_random_uuid(), 'DRH', 'Direction des Ressources Humaines', 'HR', false, 'ROLE_VALIDATEUR_N1_DRH', NULL, true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- Ensure INFO department exists (two-level)
INSERT INTO departments (id, code, name_fr, name_en, requires_n2, n1_role, n2_role, is_active, created_at, updated_at)
VALUES (gen_random_uuid(), 'INFO', 'Informatique', 'IT', true, 'ROLE_VALIDATEUR_N1_INFO', 'ROLE_VALIDATEUR_N2_INFO', true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

