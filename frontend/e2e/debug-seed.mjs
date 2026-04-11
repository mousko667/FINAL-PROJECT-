import pkg from 'pg';
import bcrypt from 'bcryptjs';
const { Client } = pkg;

const client = new Client({ connectionString: 'postgres://postgres:dany@localhost:5432/oct_invoice_dev' });
await client.connect();
const hash = await bcrypt.hash('password123', 10);
console.log('Hash generated OK:', hash.substring(0, 10));

const result = await client.query(
  `INSERT INTO users (id, username, password_hash, email, first_name, last_name, preferred_lang, is_active, created_at, updated_at)
   VALUES (gen_random_uuid(), 'e2e_test_user', $1, 'e2e_test@oct.test', 'E2E', 'Test', 'fr', true, NOW(), NOW())
   ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash`,
  [hash]
);
console.log('Rows affected:', result.rowCount);
await client.end();
