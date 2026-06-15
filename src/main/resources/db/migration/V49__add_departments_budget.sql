-- P11-52 (REQ-20): per-department annual budget, used by the budget-vs-actual report (REQ-21).
-- Nullable: a department without a set budget is reported as "no budget defined" rather than zero.
ALTER TABLE departments
    ADD COLUMN budget NUMERIC(15, 2);
