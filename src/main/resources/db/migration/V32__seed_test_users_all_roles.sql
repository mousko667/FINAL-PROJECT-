-- V32: Seed one test user per role for development and demonstration.
-- All passwords are BCrypt hash of "Test1234!" (strength 12).
-- MFA is pre-verified (mfa_verified=true, mfa_enabled=false) so these accounts
-- can be used immediately without completing MFA setup.
-- These accounts are for DEV/TEST only — never use in production.

DO $$
DECLARE
    -- BCrypt strength 12 hash of "Test1234!" — for DEV/TEST only
    pw_hash TEXT := '$2b$12$FFscGrU53UfITyv/j1yDS.hptsmXAPJ7dLKZuNsjjRu/qK4mOXF.e';

    -- Role UUIDs
    role_admin       UUID;
    role_daf         UUID;
    role_aa          UUID;
    role_drh         UUID;
    role_dg          UUID;
    role_info_n1     UUID;
    role_info_n2     UUID;
    role_term        UUID;
    role_com         UUID;
    role_qhsse       UUID;
    role_infra_n1    UUID;
    role_infra_n2    UUID;
    role_tech_n1     UUID;
    role_tech_n2     UUID;
    role_supplier    UUID;

    -- Department UUIDs
    dept_drh    UUID;
    dept_dg     UUID;
    dept_fin    UUID;
    dept_info   UUID;
    dept_term   UUID;
    dept_com    UUID;
    dept_qhsse  UUID;
    dept_infra  UUID;
    dept_tech   UUID;

    -- User UUIDs
    u_daf        UUID := gen_random_uuid();
    u_aa         UUID := gen_random_uuid();
    u_drh        UUID := gen_random_uuid();
    u_dg         UUID := gen_random_uuid();
    u_info_n1    UUID := gen_random_uuid();
    u_info_n2    UUID := gen_random_uuid();
    u_term       UUID := gen_random_uuid();
    u_com        UUID := gen_random_uuid();
    u_qhsse      UUID := gen_random_uuid();
    u_infra_n1   UUID := gen_random_uuid();
    u_infra_n2   UUID := gen_random_uuid();
    u_tech_n1    UUID := gen_random_uuid();
    u_tech_n2    UUID := gen_random_uuid();
    u_supplier   UUID := gen_random_uuid();

    -- Supplier record UUID
    s_oct_test   UUID := gen_random_uuid();
BEGIN
    -- Fetch role IDs
    SELECT id INTO role_admin    FROM roles WHERE name = 'ROLE_ADMIN';
    SELECT id INTO role_daf      FROM roles WHERE name = 'ROLE_DAF';
    SELECT id INTO role_aa       FROM roles WHERE name = 'ROLE_ASSISTANT_COMPTABLE';
    SELECT id INTO role_drh      FROM roles WHERE name = 'ROLE_VALIDATEUR_N1_DRH';
    SELECT id INTO role_dg       FROM roles WHERE name = 'ROLE_VALIDATEUR_N1_DG';
    SELECT id INTO role_info_n1  FROM roles WHERE name = 'ROLE_VALIDATEUR_N1_INFO';
    SELECT id INTO role_info_n2  FROM roles WHERE name = 'ROLE_VALIDATEUR_N2_INFO';
    SELECT id INTO role_term     FROM roles WHERE name = 'ROLE_VALIDATEUR_N1_TERM';
    SELECT id INTO role_com      FROM roles WHERE name = 'ROLE_VALIDATEUR_N1_COM';
    SELECT id INTO role_qhsse    FROM roles WHERE name = 'ROLE_VALIDATEUR_N1_QHSSE';
    SELECT id INTO role_infra_n1 FROM roles WHERE name = 'ROLE_VALIDATEUR_N1_INFRA';
    SELECT id INTO role_infra_n2 FROM roles WHERE name = 'ROLE_VALIDATEUR_N2_INFRA';
    SELECT id INTO role_tech_n1  FROM roles WHERE name = 'ROLE_VALIDATEUR_N1_TECH';
    SELECT id INTO role_tech_n2  FROM roles WHERE name = 'ROLE_VALIDATEUR_N2_TECH';
    SELECT id INTO role_supplier FROM roles WHERE name = 'ROLE_SUPPLIER';

    -- Fetch department IDs
    SELECT id INTO dept_drh   FROM departments WHERE code = 'DRH';
    SELECT id INTO dept_dg    FROM departments WHERE code = 'DG';
    SELECT id INTO dept_fin   FROM departments WHERE code = 'FIN';
    SELECT id INTO dept_info  FROM departments WHERE code = 'INFO';
    SELECT id INTO dept_term  FROM departments WHERE code = 'TERM';
    SELECT id INTO dept_com   FROM departments WHERE code = 'COM';
    SELECT id INTO dept_qhsse FROM departments WHERE code = 'QHSSE';
    SELECT id INTO dept_infra FROM departments WHERE code = 'INFRA';
    SELECT id INTO dept_tech  FROM departments WHERE code = 'TECH';

    -- Insert test users one by one, skipping on any unique constraint conflict (username OR email)
    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_daf,'daf','daf@oct.local',pw_hash,'Marie','Dubois','fr',true,false,true,dept_fin,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='daf' OR email='daf@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_aa,'aa','aa@oct.local',pw_hash,'Jean','Martin','fr',true,false,true,dept_fin,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='aa' OR email='aa@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_drh,'drh','drh@oct.local',pw_hash,'Fatou','Ndiaye','fr',true,false,true,dept_drh,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='drh' OR email='drh@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_dg,'dg','dg@oct.local',pw_hash,'Pierre','Legrand','fr',true,false,true,dept_dg,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='dg' OR email='dg@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_info_n1,'rsi','rsi@oct.local',pw_hash,'Koffi','Mensah','fr',true,false,true,dept_info,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='rsi' OR email='rsi@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_info_n2,'dsi','dsi@oct.local',pw_hash,'Sophie','Bernard','fr',true,false,true,dept_info,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='dsi' OR email='dsi@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_term,'dex','dex@oct.local',pw_hash,'Moussa','Coulibaly','fr',true,false,true,dept_term,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='dex' OR email='dex@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_com,'com','com@oct.local',pw_hash,'Aminata','Diallo','fr',true,false,true,dept_com,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='com' OR email='com@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_qhsse,'qhsse','qhsse@oct.local',pw_hash,'Ibrahim','Traore','fr',true,false,true,dept_qhsse,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='qhsse' OR email='qhsse@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_infra_n1,'infra','infra@oct.local',pw_hash,'Adama','Sawadogo','fr',true,false,true,dept_infra,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='infra' OR email='infra@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_infra_n2,'dir_infra','dir_infra@oct.local',pw_hash,'Claudine','Ouedraogo','fr',true,false,true,dept_infra,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='dir_infra' OR email='dir_infra@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_tech_n1,'atelier','atelier@oct.local',pw_hash,'Olivier','Kone','fr',true,false,true,dept_tech,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='atelier' OR email='atelier@oct.local');

    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang, is_active, mfa_enabled, mfa_verified, department_id, created_at, updated_at)
    SELECT u_tech_n2,'dir_tech','dir_tech@oct.local',pw_hash,'Yves','Toure','fr',true,false,true,dept_tech,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='dir_tech' OR email='dir_tech@oct.local');

    -- Insert test supplier company (idempotent)
    INSERT INTO suppliers (id, company_name, tax_id, contact_email, contact_phone, address, status, created_at, updated_at)
    VALUES (s_oct_test, 'Fournisseur Test SA', 'GA-TEST-001', 'contact@fournisseur-test.ga', '+241 00 00 00 00', 'Libreville, Gabon', 'ACTIVE', NOW(), NOW())
    ON CONFLICT (tax_id) DO NOTHING;

    -- Re-fetch the actual supplier id (may differ from s_oct_test if already existed)
    SELECT id INTO s_oct_test FROM suppliers WHERE tax_id = 'GA-TEST-001';

    -- Insert supplier user
    INSERT INTO users (id, username, email, password_hash, first_name, last_name, preferred_lang,
                       is_active, mfa_enabled, mfa_verified, supplier_id, created_at, updated_at)
    SELECT u_supplier,'supplier','supplier@oct.local',pw_hash,'Test','Fournisseur','fr',true,false,true,s_oct_test,NOW(),NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='supplier' OR email='supplier@oct.local');

    -- Assign roles by looking up the actual user ID from username (works whether inserted now or previously)
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_daf FROM users u WHERE u.username = 'daf' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_aa FROM users u WHERE u.username = 'aa' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_drh FROM users u WHERE u.username = 'drh' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_dg FROM users u WHERE u.username = 'dg' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_info_n1 FROM users u WHERE u.username = 'rsi' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_info_n2 FROM users u WHERE u.username = 'dsi' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_term FROM users u WHERE u.username = 'dex' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_com FROM users u WHERE u.username = 'com' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_qhsse FROM users u WHERE u.username = 'qhsse' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_infra_n1 FROM users u WHERE u.username = 'infra' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_infra_n2 FROM users u WHERE u.username = 'dir_infra' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_tech_n1 FROM users u WHERE u.username = 'atelier' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_tech_n2 FROM users u WHERE u.username = 'dir_tech' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, role_supplier FROM users u WHERE u.username = 'supplier' ON CONFLICT DO NOTHING;

    INSERT INTO user_roles (user_id, role_id)
    SELECT u_supplier, role_supplier WHERE EXISTS (SELECT 1 FROM users WHERE id = u_supplier)
    ON CONFLICT DO NOTHING;

END $$;
