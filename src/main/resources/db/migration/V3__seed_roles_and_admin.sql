-- Insert roles
INSERT INTO roles (name, description) VALUES
    ('ROLE_ADMIN', 'Administrator with full access'),
    ('ROLE_ACCOUNTANT', 'Accountant who can process invoices'),
    ('ROLE_APPROVER', 'Approver (N1/N2) for invoices'),
    ('ROLE_MANAGER', 'Department Manager'),
    ('ROLE_READONLY', 'Read-only access')
ON CONFLICT (name) DO NOTHING;

-- Insert admin user (password: admin123)
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

-- Assign ROLE_ADMIN to admin user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;
