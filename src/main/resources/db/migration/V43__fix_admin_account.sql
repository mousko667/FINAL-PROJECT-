-- V43 — Realign the bootstrap `admin` account so MFA-setup enforcement does not brick it (MAJEUR-6).
--
-- Root cause: V5 seeds `admin` without setting the MFA columns, so it defaults to
-- mfa_enabled=false, mfa_verified=false, mfa_secret=NULL. MfaSetupEnforcementFilter blocks
-- every non-supplier role that is not "truly verified" with `mfa_setup_required`, so a fresh
-- admin cannot perform any action. This was previously patched by hand in the DB, which does
-- not survive a re-seed / fresh environment — the fix belongs in Flyway.
--
-- Fix: mark the seeded admin as MFA-verified (trusted as-is in dev where enforceSecretCheck=false)
-- and clear any drifted/stale TOTP secret so the account starts from a clean, re-enrollable state.
-- No plaintext secret is ever written. The password_hash already set in V5 is correct and is not
-- touched here. The statement is an idempotent UPDATE keyed on the username — safe to run against
-- any environment, and a no-op if the row already matches.
UPDATE users
SET mfa_verified = true,
    mfa_secret   = NULL,
    mfa_enabled  = false
WHERE username = 'admin';
