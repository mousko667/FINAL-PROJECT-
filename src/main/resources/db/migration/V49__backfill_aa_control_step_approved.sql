-- V49 (AUDIT-036) — Backfill the "Controle AA" approval step frozen at PENDING.
--
-- assignAA created step_order 0 ("Controle AA") with status PENDING but nothing ever closed it,
-- unlike validateN1/validateN2 which create their step directly APPROVED. Result: the AA control
-- step showed "EN ATTENTE" forever — 8 PENDING rows, zero APPROVED — even on paid/archived invoices,
-- which is audit data lying about a completed control. The code is fixed to create the step APPROVED
-- going forward; this migration repairs the existing rows.
--
-- Scope, deliberately narrow:
--   * only step_order = 0 AND step_name_fr = 'Controle AA'  (the AA control step only);
--   * only status = 'PENDING'  → REJECTED AA steps are a real terminal outcome, left untouched;
--   * action_at is backfilled from created_at (the closest available timestamp for a control that
--     was performed at assignment time), never overwriting an action_at that already exists.
--
-- Retention safety: this is an UPDATE, not a DELETE. The V33 retention triggers are BEFORE DELETE
-- only, and do not even cover approval_steps — so there is no conflict with financial retention.

UPDATE approval_steps
SET status    = 'APPROVED',
    action_at = COALESCE(action_at, created_at)
WHERE step_order = 0
  AND step_name_fr = 'Controle AA'
  AND status = 'PENDING';
