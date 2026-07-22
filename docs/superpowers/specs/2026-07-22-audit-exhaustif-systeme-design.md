# Audit exhaustif du système OCT Invoice — Méthodologie & spec

Date : 2026-07-22
Objectif : détecter **toutes** les erreurs du système (logique, fonctionnelle, visuelle,
sécurité, incohérences, dette), établir un backlog priorisé, puis **tout corriger** jusqu'à
zéro problème résiduel. Finalité : système livrable pour soutenance — zéro incohérence tolérée.

## Contexte système (vérifié 2026-07-22)

- Monorepo `invoice-system/` : backend Spring Boot (40 controllers, 51 services, 47 migrations
  Flyway) + frontend React/TS (56 pages/composants non-test), i18n FR/EN, dark mode, PDF (iText),
  MFA, WebSocket, portail fournisseur séparé.
- **15 rôles** : admin, daf, aa (assistant comptable), 11 validateurs N1/N2 par département,
  supplier. Séparation des devoirs (SoD) stricte : ADMIN ≠ accès financier.
- **Machine à états** : BROUILLON→SOUMIS→EN_VALIDATION_N1→[EN_VALIDATION_N2]→VALIDE→BON_A_PAYER→PAYE→ARCHIVE.
- Docker (5 services) actuellement **éteint** — à relancer pour l'audit runtime/visuel.
- Playwright MCP disponible → audit visuel piloté par l'agent (pas de captures fournies à la main).
- Historique : audits antérieurs (QA_AUDIT_EXHAUSTIF, SOD_AUDIT, findings N1→N25 tous traités).
  Ce nouvel audit **repart à neuf** et vise l'exhaustivité, pas l'incrément. Prochain PROB = PROB-131.

## Décisions de cadrage (validées avec l'utilisateur)

1. **Finalité** : système livrable / soutenance → exigence maximale, rapport professionnel.
2. **Portée d'exécution** : auditer → backlog priorisé → **valider le plan avec l'utilisateur** →
   corriger tout par vagues de priorité jusqu'à zéro problème.
3. **Modèles** : Opus/Fable pour le raisonnement (compréhension, audit, priorisation, plan) ;
   sous-agents **Sonnet** pour les corrections répétitives/mécaniques (relues par Opus avant merge).
4. **Structure** : une phase = une session ; fin de phase → commit + prompt de reprise paste-ready.
5. **Écart PO fournisseur** (bug pré-identifié) : documenté comme finding, décision « corriger /
   hors-scope » prise en revue de backlog (P5), cahier des charges sous les yeux.

## Bug pré-identifié (à inscrire comme AUDIT-001)

Le portail fournisseur n'expose **aucun endpoint de lecture des bons de commande (PO)**.
Le fournisseur doit connaître le numéro de commande hors système (email/appel/contrat) et le
**recopie à la main** dans sa facture — `purchaseOrderId` est un champ plat déclaratif
(`Invoice.java:138`) vérifié seulement par le three-way matching côté OCT
(`SupplierPortalController.java:273`). Beaucoup de systèmes affichent au contraire les PO ouverts
pour « facturer contre une commande » (fiabilise le matching, évite les fautes de frappe).
Sévérité proposée : écart fonctionnel (P2/P3) — décision en P5.

## Phases (chacune = une session)

| Phase | Titre | Modèle | Livrable |
|---|---|---|---|
| P0 | Amorçage + compréhension système | Opus/Fable | `AUDIT_SYSTEM_MODEL.md` + `AUDIT_MASTER.md` initialisé |
| P1 | Audit code statique (backend + frontend) | Opus | Findings logique, sécurité, validation, SoD, permissions, code mort/dupliqué, dette |
| P2 | Audit visuel + responsive (Playwright, tous rôles) | Opus (pilote + sous-agents) | Captures desktop+mobile par page×rôle, findings UI |
| P3 | Audit fonctionnel end-to-end (QA par rôle) | Opus (pilote + sous-agents) | Boutons morts, formulaires, validations, navigation, cas limites, permissions, écart PO |
| P4 | Audit transverse (perf, i18n, a11y, cohérence, erreurs) | Opus | Findings N+1, i18n manquant, incohérences, gestion d'erreurs |
| P5 | Consolidation → backlog priorisé + plan | Opus/Fable | `AUDIT_BACKLOG.md` + plan validé avec l'utilisateur |
| P6 | Correction par vagues | Sonnet (sous-agents), Opus relit | Corrections + tests verts + revue + commit par lot jusqu'à backlog=0 |

## Les 5 garde-fous

1. **Registre unique `AUDIT_MASTER.md`** — persiste entre sessions. Chaque finding :
   `AUDIT-NNN | Phase | Module | Sévérité (P0/P1/P2/P3) | Titre | Localisation (fichier:ligne) |
   Cause probable | Preuve runtime | Solution proposée | Dépendances | Statut`.
   ID jamais réutilisé, finding jamais réévalué deux fois.
2. **Preuve runtime obligatoire** (règle `verify-runtime-not-snapshot`) : tout finding
   visuel/fonctionnel exige une preuve d'exécution (capture Playwright, trace réseau, log console).
   Suppose Docker relancé au début de P2/P3.
3. **Couverture = matrice page × rôle** générée depuis `AppRoutes.tsx` × 15 rôles ; chaque cellule
   cochée « vue / testée ». L'exhaustivité devient mesurable.
4. **Bon modèle par phase + sous-agents** : Opus pilote, dispatch de sous-agents par lot pour
   paralléliser (P2/P3/P4) sans exploser le contexte ; corrections mécaniques → Sonnet, relues par Opus.
5. **Gate de complétude par phase** : phase « terminée » ssi registre à jour + commit +
   **0 test cassé** (`no-failures-on-task-completion`) + prompt de reprise généré.
   P6 se termine ssi aucun finding OUVERT ≠ HORS-SCOPE.

## Dimensions d'audit couvertes (checklist exhaustive)

Architecture · design patterns · logique métier · machine à états · sécurité (authz/authn, IDOR,
injection, secrets) · séparation des devoirs (SoD) · validation des données · permissions cross-rôle ·
UX · UI · responsive (desktop/mobile) · dark mode · accessibilité (contraste, focus, ARIA) ·
cohérence inter-écrans · i18n FR/EN (clés manquantes, encodage) · performance (N+1, requêtes lentes,
bundle) · gestion des erreurs · navigation · boutons/formulaires non fonctionnels · pages
mal dimensionnées/débordement · composants inutilisés · code dupliqué · dette technique · tests
manquants · **suggestions d'amélioration de cohérence** (ajouts qui rendent le tout plus cohérent).

Chaque finding porte : gravité · impact · localisation · cause probable · solution proposée.

## Disciplines projet (héritées des mémoires, à respecter dans chaque phase)

- Répondre **en français**. Devise **XAF** (jamais XOF).
- `messages_fr.properties` = **ISO-8859-1 / ASCII \uXXXX** ; jamais d'append UTF-8 brut (corrompt les accents).
- **ROLE_ADMIN ≠ accès financier** : critère d'audit (SoD), pas un bug à « corriger » en ouvrant l'accès.
- **Une branche = un sujet** ; merge ff-only puis suppression ; push tous les 10 commits ; PR en fin de lot.
- **Vérifier le code réel** avant de conclure (règle `verify-task-status-before-spec`) — ne pas croire un resume-prompt.
- Ne pas rappeler le `git push` à chaque tâche ; l'utilisateur gère.
- Prochain identifiant PROB = **PROB-131** ; prochain finding d'audit = **AUDIT-002** (AUDIT-001 = écart PO).

## Critère de succès global

Le registre `AUDIT_MASTER.md` ne contient plus aucun finding au statut OUVERT ou EN COURS
(tout est CORRIGÉ ou explicitement HORS-SCOPE validé par l'utilisateur), la suite de tests est
verte (0 échec back + front), et une passe de vérification runtime finale confirme le comportement
sur les rôles clés (admin, daf, aa, un validateur N1, un N2, supplier).
