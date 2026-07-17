-- V47: Enforce separation of duties (SoD) between ROLE_ASSISTANT_COMPTABLE and ROLE_DAF.
--
-- Why: in the RUNNING DEVELOPMENT DATABASE the `daf` account held BOTH
-- ROLE_ASSISTANT_COMPTABLE and ROLE_DAF — the only account there cumulating two business roles.
-- That breaks separation of duties: the DAF could approve (bon a payer) invoices they entered
-- themselves.
--
-- IMPORTANT nuance (verified 2026-07-17): that cumulation exists in NO migration. V34
-- (`V34__seed_test_users.sql`, l.156) grants the `daf` account ROLE_DAF only. The cumulation was
-- introduced by a MANUAL, never-versioned role assignment in the development database. So:
--   * on a fresh database, the DELETE below is a no-op (nothing to clean);
--   * on the development database, it is the only VERSIONED way to correct the existing drift;
--   * the TRIGGER is the real, lasting contribution: it prevents the drift from reappearing.
-- Do not "simplify" this migration by dropping the DELETE: the drift is real where it matters.
--
-- Three data-only + constraint operations:
--   1. Remove ROLE_ASSISTANT_COMPTABLE from the `daf` account (fixes the observed drift).
--   2. Seed a second assistant comptable (`aa2`) as a fallback: once `daf` loses the AA role,
--      `aa` would be the ONLY AA in the system, and the AA control step (EN_CONTROLE_AA) is a
--      mandatory pass-through. Approval delegation CANNOT cover the AA: approval_delegations
--      is keyed on department_code NOT NULL and the AA is transverse (no department), so
--      ApprovalServiceImpl.checkRole would look for a department named ASSISTANT_COMPTABLE
--      that does not exist. A second account is the only viable fallback.
--   3. Add a trigger rejecting any future AA+DAF cumulation. It lives in the database rather
--      than in Java because there is no central UserServiceImpl: role writes go through User,
--      UserMapper and UserCsvService (CSV import), so an application-side guard on a single
--      path would be bypassed by the importer.
--
-- Idempotent: safe to re-run.

-- 1. Remove the AA role from the DAF account
DELETE FROM user_roles ur
USING users u, roles r
WHERE ur.user_id = u.id
  AND ur.role_id = r.id
  AND u.username = 'daf'
  AND r.name = 'ROLE_ASSISTANT_COMPTABLE';

-- 2. Seed the fallback assistant comptable account (password: Test1234!, same BCrypt hash
--    as the existing `aa` test account)
INSERT INTO users (username, email, password_hash, first_name, last_name, preferred_lang, is_active)
SELECT 'aa2', 'aa2@oct.local',
       '$2b$12$FFscGrU53UfITyv/j1yDS.hptsmXAPJ7dLKZuNsjjRu/qK4mOXF.e',
       'Bernard', 'Comptable', 'fr', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'aa2');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'aa2'
  AND r.name = 'ROLE_ASSISTANT_COMPTABLE'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 3. Prevent any future AA+DAF cumulation, on every write path
CREATE OR REPLACE FUNCTION enforce_sod_aa_daf() RETURNS TRIGGER AS $$
DECLARE
    incoming_role TEXT;
    conflicting   TEXT;
BEGIN
    SELECT name INTO incoming_role FROM roles WHERE id = NEW.role_id;

    IF incoming_role = 'ROLE_ASSISTANT_COMPTABLE' THEN
        conflicting := 'ROLE_DAF';
    ELSIF incoming_role = 'ROLE_DAF' THEN
        conflicting := 'ROLE_ASSISTANT_COMPTABLE';
    ELSE
        RETURN NEW;
    END IF;

    IF EXISTS (
        SELECT 1 FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        WHERE ur.user_id = NEW.user_id
          AND r.name = conflicting
    ) THEN
        RAISE EXCEPTION 'SoD violation: user cannot hold both ROLE_ASSISTANT_COMPTABLE and ROLE_DAF';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_sod_aa_daf ON user_roles;
CREATE TRIGGER trg_enforce_sod_aa_daf
    BEFORE INSERT OR UPDATE ON user_roles
    FOR EACH ROW EXECUTE FUNCTION enforce_sod_aa_daf();
