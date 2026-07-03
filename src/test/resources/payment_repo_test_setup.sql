-- Setup for PaymentRepositoryTest
-- Inserts minimum required rows to satisfy FK / NOT NULL constraints.

INSERT INTO departments (id, code, name_fr, name_en, requires_n2, n1_role, is_active, created_at, updated_at)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'TST', 'Test FR', 'Test EN', false, 'ROLE_TEST', true,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (id, username, email, password_hash, first_name, last_name, role, active, failed_login_attempts,
                   mfa_enabled, mfa_verified, created_at, updated_at)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'testuser', 'test@test.com', 'hash',
        'Test', 'User', 'ROLE_COMPTABLE', true, 0, false, false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO invoices (id, reference_number, amount, currency, status, department_id, submitted_by,
                      issue_date, due_date, version, created_at, updated_at)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'INV-TEST-001', 1000.00, 'XAF', 'PAYE',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        CURRENT_DATE, CURRENT_DATE + 30, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
