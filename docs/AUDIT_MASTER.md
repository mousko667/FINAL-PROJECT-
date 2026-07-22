# AUDIT_MASTER — Registre unique de l'audit exhaustif

> **Registre persistant sur les 7 phases (P0→P6).** C'est la source de vérité unique des findings.
> Méthodologie : `docs/superpowers/specs/2026-07-22-audit-exhaustif-systeme-design.md`.
> Modèle du système : `docs/AUDIT_SYSTEM_MODEL.md`. Couverture : `docs/AUDIT_COVERAGE.md`.
>
> Ouvert le **2026-07-22** sur la branche `audit/exhaustif-p0-comprehension` (depuis `main` = `c4f5e11`).

---

## Règles du registre — à ne jamais enfreindre

1. **Un ID n'est jamais réutilisé.** Numérotation continue `AUDIT-001`, `AUDIT-002`, … Prochain ID
   libre : **AUDIT-002**.
2. **Un finding n'est jamais réévalué deux fois.** S'il ressort dans une phase ultérieure, on
   complète la ligne existante (preuve runtime, dépendances) — on n'en crée pas une nouvelle.
3. **Preuve runtime obligatoire** pour tout finding visuel ou fonctionnel (capture Playwright, trace
   réseau, log console). Un finding sans preuve reste « à confirmer » et ne peut pas passer en P6.
4. **Aucun finding n'est émis en P0.** P0 = compréhension + mise en place. Les findings commencent
   en P1. AUDIT-001 est la seule exception : il était pré-identifié avant le lancement de l'audit.
5. **Statuts autorisés** : `OUVERT` · `EN COURS` · `CORRIGÉ` · `HORS-SCOPE`.
   `HORS-SCOPE` exige une validation explicite de l'utilisateur, consignée dans la ligne.
6. **Sévérités** : `P0` bloquant (le système est faux ou dangereux) · `P1` majeur (fonction cassée
   ou faille SoD) · `P2` mineur (gêne réelle, contournable) · `P3` cosmétique / dette / suggestion.
7. **Fin d'audit (P6)** : plus aucune ligne en `OUVERT` ou `EN COURS`.

---

## Registre

| ID | Phase | Module | Sévérité | Titre | Localisation | Cause probable | Preuve runtime | Solution proposée | Dépendances | Statut |
|---|---|---|---|---|---|---|---|---|---|---|
| AUDIT-001 | P0 | supplier-portal | À confirmer (P2/P3 proposé) | Portail fournisseur : aucun endpoint de lecture des PO — le fournisseur recopie le numéro de commande à la main | `src/main/java/com/oct/invoicesystem/domain/invoice/model/Invoice.java:138` (`purchaseOrderId`) · `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierPortalController.java:273` (`toInvoice`) | `purchaseOrderId` est un champ **plat déclaratif** (UUID repris tel quel de la requête), pas une relation « facturer contre une commande ». `SupplierPortalController` (classe `@PreAuthorize("hasRole('SUPPLIER')")`, base `/api/v1/supplier`) n'expose que factures / documents / profil / dashboard — **aucun `GET` de PO ouverts**. Le fournisseur doit donc connaître le numéro de commande hors système (email, appel, contrat) et le saisir manuellement ; seul le three-way matching côté OCT le vérifie a posteriori. | À vérifier en **P3** | Ajouter un endpoint `GET /api/v1/supplier/purchase-orders` listant les PO **ouverts du fournisseur connecté**, puis une sélection (liste déroulante / recherche) dans `SupplierInvoiceSubmitPage` au lieu d'une saisie libre. Fiabilise le matching et supprime les fautes de frappe. **Décision corriger / hors-scope à prendre en P5**, cahier des charges sous les yeux. | — | OUVERT |

---

## Synthèse par statut

| Statut | Nombre |
|---|---|
| OUVERT | 1 |
| EN COURS | 0 |
| CORRIGÉ | 0 |
| HORS-SCOPE | 0 |
| **Total** | **1** |

## Synthèse par sévérité

| Sévérité | Nombre |
|---|---|
| P0 (bloquant) | 0 |
| P1 (majeur) | 0 |
| P2 (mineur) | 0 |
| P3 (cosmétique / dette) | 0 |
| À confirmer | 1 |

## Synthèse par phase

| Phase | Findings émis | État |
|---|---|---|
| P0 — Amorçage + compréhension | 1 (pré-identifié) | ✅ terminée |
| P1 — Audit code statique | — | à faire |
| P2 — Audit visuel + responsive | — | à faire |
| P3 — Audit fonctionnel end-to-end | — | à faire |
| P4 — Audit transverse (perf, i18n, a11y) | — | à faire |
| P5 — Consolidation + backlog priorisé | — | à faire |
| P6 — Correction par vagues | — | à faire |

---

## Pistes ouvertes en P0 (à instruire en P1 — pas encore des findings)

Ces observations viennent de la lecture du code en P0. Elles **ne sont pas** des findings : aucune n'a
été vérifiée contre le cahier des charges ni contre le runtime. La P1 doit soit les promouvoir en
`AUDIT-NNN`, soit les écarter avec une justification.

| # | Observation | Où | À trancher |
|---|---|---|---|
| O-1 | `EN_CONTROLE_AA` existe dans le code mais pas dans `CLAUDE.md §5` / `docs/WORKFLOW.md` | `InvoiceStatus.java`, `StateMachineConfig.java:58-74` | doc en retard, ou étape non spécifiée ? |
| O-2 | `POST /validate-n1` autorise `hasRole('DAF')` en plus des validateurs N1 | `ApprovalController.java:81` | validateur de secours voulu, ou reliquat SoD ? |
| O-3 | Backend `/api/v1/suppliers/**` ouvert à ADMIN + AA, alors que le frontend réserve les fournisseurs à AA seul (commentaire N16) | `SupplierController.java:59-191` vs `Sidebar.tsx:196`, `SuppliersPage.tsx:259` | backend trop permissif ? impact SoD |
| O-4 | Override de matching = DAF seul dans le code, « DAF ou ADMIN » dans `CLAUDE.md §9` | `InvoiceController.java:322` | doc fausse ou code incomplet ? |
| O-5 | Transitions `RECORD_PAYMENT` et `ARCHIVE` sans aucune garde de machine à états | `StateMachineConfig.java:100-108` | contrôle équivalent en service ? |
| O-6 | `InvoiceDetailPage` n'a pas de `PageRoleGuard`, contrairement à `InvoiceListPage` | `frontend/src/pages/InvoiceDetailPage.tsx` | protection backend suffisante ? |
| O-7 | `/payments/alert-rules` : route + garde existent, aucune entrée de navigation | `AppRoutes.tsx:99`, `Sidebar.tsx` | page orpheline volontaire ? |
| O-8 | Portail fournisseur : aucun contrôle vu que le `purchaseOrderId` saisi appartient bien au fournisseur | `SupplierPortalController.java:260-281` | IDOR potentiel — à tester en P3 |
| O-9 | `APPROVER_ROLES` (constante `DelegationController`) non résolue en P0 | `DelegationController.java` | composition exacte à lire |
| O-10 | `DepartmentTransitionGuard` : critère « deux niveaux » non lu (drapeau en base ou liste en dur ?) | `domain/workflow/guard/DepartmentTransitionGuard.java` | conformité au cahier des charges |
