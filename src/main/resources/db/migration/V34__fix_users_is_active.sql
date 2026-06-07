-- Fix: set is_active=true for all seeded users that were created with is_active=false
-- These users were inserted before the V32 migration's WHERE NOT EXISTS condition,
-- so V32 skipped the update. All operational staff accounts must be active.
UPDATE users SET is_active = true WHERE is_active = false AND username IN (
    'admin', 'daf', 'aa', 'drh', 'dg', 'rsi', 'dsi', 'dex', 'com', 'qhsse',
    'infra', 'dir_infra', 'atelier', 'dir_tech', 'supplier'
);
