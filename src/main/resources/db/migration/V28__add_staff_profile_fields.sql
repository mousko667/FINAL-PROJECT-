ALTER TABLE users
ADD COLUMN employee_id VARCHAR(100),
ADD COLUMN department_id UUID REFERENCES departments(id),
ADD COLUMN approval_limit NUMERIC(15, 2);

CREATE INDEX idx_users_employee_id ON users(employee_id);
CREATE INDEX idx_users_department_id ON users(department_id);
