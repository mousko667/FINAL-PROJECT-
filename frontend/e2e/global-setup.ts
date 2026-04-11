import { FullConfig } from '@playwright/test';

// Use createRequire to load CommonJS modules in an ESM context
import { createRequire } from 'module';
const require = createRequire(import.meta.url);

// eslint-disable-next-line @typescript-eslint/no-require-imports
const pg = require('pg');
// eslint-disable-next-line @typescript-eslint/no-require-imports
const bcrypt = require('bcryptjs');

const { Client } = pg;
const TEST_PASSWORD = 'password123';
const BCRYPT_COST = 12;

async function globalSetup(_config: FullConfig) {
  console.log('Running global setup to seed the database...');
  const dbUrl = process.env.DATABASE_URL || 'postgres://postgres:dany@localhost:5432/oct_invoice_dev';

  const client = new Client({ connectionString: dbUrl });

  try {
    await client.connect();

    // Match backend BCrypt strength and fail fast if the generated hash is unusable.
    const passwordHash: string = await bcrypt.hash(TEST_PASSWORD, BCRYPT_COST);
    if (!bcrypt.compareSync(TEST_PASSWORD, passwordHash)) {
      throw new Error('Generated E2E password hash did not validate against the test password');
    }
    console.log('BCrypt hash generated successfully');

    // Insert E2E-specific roles
    const roles = [
      'ROLE_ASSISTANT_COMPTABLE',
      'ROLE_VALIDATEUR_N1_DRH',
      'ROLE_VALIDATEUR_N1_INFO',
      'ROLE_VALIDATEUR_N2_INFO',
      'ROLE_DAF',
      'ROLE_AUDITEUR'
    ];

    for (const roleName of roles) {
      await client.query(
        `INSERT INTO roles (id, name, description, created_at)
         VALUES (gen_random_uuid(), $1, $1, NOW())
         ON CONFLICT (name) DO NOTHING`,
        [roleName]
      );
    }
    console.log('Roles seeded');

    // Insert E2E test users
    const users = [
      { username: 'e2e_assistant', role: 'ROLE_ASSISTANT_COMPTABLE' },
      { username: 'e2e_n1_drh',   role: 'ROLE_VALIDATEUR_N1_DRH'   },
      { username: 'e2e_daf',      role: 'ROLE_DAF'                  },
      { username: 'e2e_n1_info',  role: 'ROLE_VALIDATEUR_N1_INFO'   },
      { username: 'e2e_n2_info',  role: 'ROLE_VALIDATEUR_N2_INFO'   },
    ];

    for (const u of users) {
      await client.query(
        `INSERT INTO users (id, username, password_hash, email, first_name, last_name, preferred_lang, is_active, created_at, updated_at)
         VALUES (gen_random_uuid(), $1, $2, $3, $1, 'E2E', 'fr', true, NOW(), NOW())
         ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash`,
        [u.username, passwordHash, `${u.username}@oct.test`]
      );

      await client.query(
        `INSERT INTO user_roles (user_id, role_id)
         SELECT u.id, r.id FROM users u CROSS JOIN roles r
         WHERE u.username = $1 AND r.name = $2
         ON CONFLICT DO NOTHING`,
        [u.username, u.role]
      );
    }
    console.log('Users seeded');

    // Ensure DRH dept exists (single-level)
    await client.query(
      `INSERT INTO departments (id, code, name_fr, name_en, requires_n2, n1_role, n2_role, is_active, created_at, updated_at)
       VALUES (gen_random_uuid(), 'DRH', 'Direction des Ressources Humaines', 'HR', false, 'ROLE_VALIDATEUR_N1_DRH', NULL, true, NOW(), NOW())
       ON CONFLICT (code) DO NOTHING`
    );

    // Ensure INFO dept exists (two-level)
    await client.query(
      `INSERT INTO departments (id, code, name_fr, name_en, requires_n2, n1_role, n2_role, is_active, created_at, updated_at)
       VALUES (gen_random_uuid(), 'INFO', 'Informatique', 'IT', true, 'ROLE_VALIDATEUR_N1_INFO', 'ROLE_VALIDATEUR_N2_INFO', true, NOW(), NOW())
       ON CONFLICT (code) DO NOTHING`
    );
    console.log('Departments seeded');

    console.log('✓ Global setup complete. E2E test data seeded.');
  } catch (error) {
    console.error('✗ Error in global-setup.ts:', error);
    throw error;
  } finally {
    await client.end();
  }
}

export default globalSetup;

