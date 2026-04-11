const { Client } = require('pg');
const bcrypt = require('bcryptjs');

async function main() {
  const client = new Client({ connectionString: 'postgres://postgres:dany@localhost:5432/oct_invoice_dev' });
  await client.connect();
  const hash = await bcrypt.hash('password123', 10);
  console.log('Hash generated:', hash.substring(0, 20));

  const result = await client.query(
    `INSERT INTO users (id, username, password_hash, email, first_name, last_name, preferred_lang, is_active, created_at, updated_at)
     VALUES (gen_random_uuid(), 'e2e_assistant', $1, 'e2e_assistant@oct.test', 'E2E', 'Assistant', 'fr', true, NOW(), NOW())
     ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash`,
    [hash]
  );
  console.log('Users rows affected:', result.rowCount);
  await client.end();
}
main().catch(console.error);
