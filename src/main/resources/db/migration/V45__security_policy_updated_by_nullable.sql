-- P11-40 #4: the startup-seeded default security policy has no human author, so updated_by
-- must allow NULL. A policy saved via the admin UI always sets it. (V44 created it NOT NULL.)
ALTER TABLE security_policy ALTER COLUMN updated_by DROP NOT NULL;
