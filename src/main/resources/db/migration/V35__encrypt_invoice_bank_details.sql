-- V35: Migration des coordonnées bancaires existantes sur la table invoices.
-- Les lignes dont supplier_bank_details n'est pas NULL et ne commence pas par
-- le préfixe AES base64 doivent être nullifiées (elles sont en clair et ne peuvent
-- pas être rechiffrées par SQL). Les nouvelles valeurs seront chiffrées par JPA.
-- IMPORTANT: Sauvegarder les données avant de lancer cette migration en production.

-- Nullifier les coordonnées bancaires en clair (non chiffrées).
-- Les coordonnées chiffrées par EncryptionUtil commencent par un bloc base64 valide.
-- En l'absence d'un moyen de rechiffrer depuis SQL, on vide le champ pour forcer
-- une nouvelle saisie via le portail fournisseur ou l'interface admin.
UPDATE invoices
SET supplier_bank_details = NULL
WHERE supplier_bank_details IS NOT NULL;

-- Note pour la production: avant d'appliquer cette migration, exporter les
-- coordonnées bancaires existantes via:
--   COPY (SELECT id, supplier_bank_details FROM invoices WHERE supplier_bank_details IS NOT NULL)
--   TO '/tmp/bank_details_backup.csv' CSV HEADER;
