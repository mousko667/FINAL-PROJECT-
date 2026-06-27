-- M7 #4 : statut de paiement (SCHEDULED/PROCESSED) + date d'execution reelle.
-- DEFAULT 'PROCESSED' rend l'historique existant coherent (paiements deja executes).
ALTER TABLE payments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED';
ALTER TABLE payments ADD COLUMN processed_date TIMESTAMP;
UPDATE payments SET processed_date = payment_date WHERE processed_date IS NULL;
