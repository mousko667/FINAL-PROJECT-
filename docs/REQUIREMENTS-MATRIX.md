# MATRICE DE CONFORMITÉ AUX REQUIREMENTS (Réf. cahier des charges 25627)

> À auditer module par module. Pour chaque sous-élément : verdict ✅ COMPLET / ⚠️ PARTIEL / ❌ ABSENT, avec **preuve** (fichier:ligne backend, composant frontend, ou test runtime). Tout ❌ ou ⚠️ devient une issue dans `ISSUES.md` avec préfixe `REQ-`.

## Règle métier transversale — MFA obligatoire
MFA **obligatoire pour tous les rôles SAUF supplier** (accounting assistant, level 1 approver, level 2 approver, CFO, administrator). Vérifier que le supplier en est dispensé ET que les 5 autres rôles ne peuvent PAS le contourner.

## Module 1 — Registration & Authentication
- Formulaire d'inscription avec sélection de rôle (supplier, accounting assistant, L1 approver, L2 approver, CFO, administrator)
- Champs spécifiques fournisseur : company name, tax ID, contact, bank details
- Champs spécifiques staff : employee ID, department, approval limits
- Indicateur de force de mot de passe
- Vérification email
- Récupération / reset de mot de passe
- Setup MFA (selon règle ci-dessus)
- Gestion de session + timeout
- Redirection dashboard selon rôle
- Écran gestion de profil
- Tracking des tentatives de connexion (lié à `isAccountNonLocked()` — voir T1)
- Auto-inscription supplier vs création staff par administrator

## Module 2 — Dashboard
- Vues par rôle : supplier / finance staff / manager
- Supplier : factures soumises, statut paiement, actions en attente
- Finance : approbations en attente, file de traitement, analyse d'âge (aging)
- Manager : demandes d'approbation, métriques, alertes budget
- Summary cards (reçues, en validation, approuvées, payées)
- Feed d'activité récente
- Quick actions
- Centre de notifications avec compteur non-lus
- KPIs de temps de traitement
- Responsive mobile

## Module 3 — Invoice Reception
- Interface de soumission fournisseur
- Upload PDF / XML / image
- **Preview extraction OCR** (vérifier si réellement implémenté ou juste mocké)
- Champs facture (numéro, date, montant, fournisseur, description)
- Liaison PO / numéro de référence
- Pièces jointes
- **Détection de doublons** (alerte)
- Confirmation avec numéro de référence
- Suivi de statut côté fournisseur
- Historique des soumissions
- Upload en masse (bulk) — ✅ COMPLET (P11-48) : `POST /invoices/{id}/documents/bulk` (multi-fichiers, rapport par fichier) + `BulkDocumentUpload` au détail facture
- API d'intégration pour soumission automatisée

## Module 4 — Validation Workflow
- Interface de configuration du workflow
- Règles de routage multi-niveaux
- Templates de checklist de validation
- Règles de validation auto (PO matching, seuils de montant, vérification fournisseur)
- File de validation en attente
- Interface de revue
- Approbation / rejet avec commentaires
- Sélection du motif de rejet
- Workflow de re-soumission
- Historique d'approbation
- Règles d'escalade pour retards
- Monitoring SLA

## Module 5 — Three-Way Matching (= Phase 9D, à vérifier en priorité)
- Interface de matching à 3 voies
- Affichage données PO
- Informations GRN (goods receipt note)
- Comparaison ligne à ligne
- Indicateurs de statut (matched / partial / mismatch)
- Identification et flagging des écarts
- Configuration des seuils de tolérance
- Override manuel avec justification
- Historique de matching
- Workflow de résolution des items non-matchés
- Export des rapports de matching

## Module 6 — Approval Workflow (state machine BAP)
- Dashboard d'approbation
- Vue détail facture pour approbateur
- Approbation / rejet avec commentaires
- Visualisation multi-niveaux
- **Délégation d'approbation** (= T6, à vérifier)
- Config des règles d'escalade
- Historique
- **Interface d'approbation mobile**
- Notifications d'approbation
- Tracking SLA
- Analytics d'approbation
- **Conformité au tableau des départements** : chaque département (RH/DRH, DG, Finance/DAF, IT/RSI→CIO/DSI, Terminal/DEX, Com&CSR, QHSSE, Infrastructure→Directeur, Atelier→Directeur Technique) avec son initiateur (Accounting Assistant) et ses niveaux L1/L2 corrects. **IT et Infrastructure et Atelier ont un Niveau 2**, les autres n'ont qu'un Niveau 1.

## Module 7 — Payment Tracking
- Dashboard de suivi
- Analyse d'âge (aging)
- Monitoring des échéances
- Statuts (scheduled / processed / paid / overdue)
- Traitement par lot (batch)
- Enregistrement de confirmation de paiement
- Génération d'avis de versement (remittance advice)
- Méthodes de paiement (virement, chèque, mobile money)
- Historique par fournisseur
- Analyse d'impact cash-flow
- Export rapports
- Config des alertes

## Module 8 — Supplier Management
- Annuaire fournisseurs (recherche + filtres)
- Gestion de profil
- Détails bancaires (chiffrés — voir T1)
- Infos fiscales + certificats
- Suivi contrats / accords
- Métriques de performance fournisseur
- Log de communication
- Catégorisation / segmentation
- Repository documentaire par fournisseur
- Workflow d'onboarding
- Accès portail self-service
- Export rapports

## Module 9 — Digital Archiving
- Repository avec structure de dossiers
- Stockage par date / fournisseur / statut
- Recherche avancée (= partie de T7 archive search)
- Visionneuse avec zoom / rotation
- Affichage métadonnées
- Versioning des factures
- Config de politique de rétention
- Contrôles archive / purge (soft-delete uniquement pour le financier)
- Logs d'accès aux documents
- Export
- Reporting de conformité

## Module 10 — Audit Trail
- Visionneuse de logs complète (= AuditController, lié T7)
- Timeline d'actions utilisateur avec filtres
- Historique de modification de facture
- Tracking des décisions d'approbation
- Log des changements de statut de paiement
- Enregistrements d'accès aux documents — ✅ COMPLET (P11-50) : table append-only `document_access_log` (V48) + hook dans `download()`
- Activité login/logout
- Export
- Alertes de détection d'anomalie — ❌ ABSENT (différé, PROB-046)
- Affichage conformité période de rétention
- Dashboard temps réel — ✅ COMPLET (P11-51) : panneau « Activité récente » auto-rafraîchi (15s) sur AdminAuditPage
- **Sous-typage de l'audit** (lié T7 — vérifier que les types d'événements sont bien différenciés, pas tous génériques)

## Module 11 — Reporting & Analytics
- Dashboard analytics avec KPI cards
- Rapports de temps de traitement
- Analytics de performance fournisseur
- Rapports d'aging
- Analyse du cycle de paiement
- Identification des goulots d'approbation
- Tendances volume / valeur
- Comparaison budget vs réel — ✅ COMPLET (P11-52) : `Department.budget` (V49) + `GET /reports/budget-vs-actual` (DAF+ASSISTANT), section ReportsPage
- Constructeur de rapports custom — ❌ ABSENT (différé, PROB-047)
- Preview + export (PDF, Excel)
- Config de rapports planifiés — ❌ ABSENT (différé, PROB-047)
- Gestionnaire de distribution — ❌ ABSENT (différé, PROB-047)
- Générateur de résumé exécutif — ❌ ABSENT (différé, PROB-047)

## Module 12 — Integration
- Dashboard de config d'intégration
- Connexion ERP (SAP, Oracle, MS Dynamics)
- Intégration système d'approvisionnement
- Connexion logiciel comptable
- Intégration bancaire pour paiements
- Intégration GED
- Interface de config API
- Gestion des webhooks
- Monitoring du statut d'intégration
- Config de planning de sync
- Log d'erreurs + résolution
- Interface de test de connexion
> NOTE : ce module est souvent hors-périmètre pour un bachelor. Si non implémenté, **ne pas le marquer comme bug** mais le documenter comme "hors-scope assumé" dans `docs/SCOPE.md` (à créer) — sauf si le cahier des charges l'exige formellement. Demander confirmation si ambigu.

## Module 13 — User & Access Management
- Console de gestion utilisateurs
- Attribution rôles / permissions
- Contrôle d'accès niveau département/équipe
- Classification de sensibilité des données — ✅ COMPLET (P11-15) : enum `DataSensitivity` + `V46` + `PATCH /invoices/{id}/sensitivity`, badge/select frontend
- Monitoring d'activité
- Gestion du statut de compte
- Import/export en masse — ✅ COMPLET (P11-16) : `GET/POST /users/(export|import)/csv`, `UserCsvService`, toolbar AdminUsersPage
- Workflow de demande d'accès — ✅ COMPLET (P11-17) : entité `AccessRequest` + `V47`, `POST /access-requests` + `GET /access-requests` + `PATCH /access-requests/{id}` (l'approbation attribue le rôle), pages `MyAccessRequestsPage` + `AdminAccessRequestsPage`, 12 tests, vérifié runtime
- Éditeur de matrice de permissions — ✅ COMPLET (P11-18) : `AdminPermissionMatrixPage` (grille users×rôles) sur `PUT /users/{id}/roles` + `GET /roles`
- Vue de gestion des sessions (= T6 ActiveSession)
- Visionneuse d'audit trail utilisateur
- Config de menu basée sur le rôle

## Module 14 — Security & Compliance
- Matrice de permissions par rôle
- Indicateurs de statut de chiffrement — ✅ COMPLET (P11-53) : couverture chiffrement au repos dans le tableau santé sécurité
- Paramètres 2FA / MFA
- Monitoring d'activité de login — ✅ COMPLET (P11-53) : tendance échecs (comptes verrouillés + tentatives) dans le tableau santé sécurité
- Statut de backup des données — ❌ ABSENT (différé, PROB-048)
- Config de politique de rétention
- Tracking d'acceptation de politique de confidentialité — ❌ ABSENT (différé, PROB-048)
- Reporting d'incident de sécurité — ❌ ABSENT (différé, PROB-048)
- Checklist de conformité (SOX, IFRS, régulations locales) — ❌ ABSENT (différé, PROB-048)
- Outils de préparation d'audit
- Calendrier de conformité — ❌ ABSENT (différé, PROB-048)
- Dashboard de santé sécurité — ✅ COMPLET (P11-53) : `GET /admin/security-health` + panneau sur SecuritySettingsPage (chiffrement, MFA %, échecs login, succès webhooks)

---

## Synthèse attendue
Produire `docs/audit/REQUIREMENTS-COVERAGE.md` : un tableau par module avec le pourcentage de couverture (✅ / total), la liste des éléments manquants, et le verdict global. Ce document est livrable pour la soutenance.
