# N8 (lot backend) — Retrait de l'endpoint alias mort /reports/summary

**Date :** 2026-07-19
**Finding audit :** N8 (🟠 Câblage) — **lot backend uniquement**
**Branche :** `fix/n8-remove-summary-alias`
**PROB :** PROB-130

## Contexte — inventaire N8

Le finding N8 (« 4 endpoints rapports sans UI + détail GRN + édition annonce inaccessibles »)
a été inventorié (code réel, 2026-07-19). Résultat :

- **4 endpoints rapports orphelins** (aucun appel dans `frontend/src`) :
  `/reports/summary`, `/reports/activity`, `/reports/payment-cycle`, `/reports/supplier/{id}/payments`.
- **Détail GRN** : `GET /goods-receipts/{id}` existe, mais `GoodsReceiptsPage` n'appelle que la liste.
- **Édition annonce** : `PUT /announcements/{id}` existe, mais `AdminAnnouncementsPage` ne fait que
  create/toggle/delete.

**Découpe décidée avec l'user (règle binôme [[binome-antigravity-review-gate]]) :**
- **Frontend → Antigravity** (nouveau lot) : câbler détail GRN, édition annonce, et UI pour
  `/activity`, `/payment-cycle`, `/supplier/{id}/payments` (endpoints back conservés car ils
  seront câblés).
- **Backend → ce lot (Claude)** : retirer le SEUL vrai code mort, l'alias `/reports/summary`.

## Problème

`ReportController.getSummary()` (`/summary`, lignes 53-58) est un **alias strict** de
`getKpis()` (`/kpis`, lignes 46-51) : les deux délèguent à `reportService.getDashboardKpis()`
et renvoient le même `DashboardKpiDTO`. Le front utilise déjà `/kpis` ; `/summary` n'est
référencé nulle part (ni front, ni test, ni back — vérifié). C'est un doublon d'API mort.

## Solution

### 1. Retirer l'endpoint alias

Supprimer la méthode `getSummary()` et son annotation `@GetMapping("/summary")` dans
`src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java` (lignes 53-58).
`getDashboardKpis()` reste utilisé par `/kpis` et `ReportBuilderService` — inchangé.

### 2. Test

Ajouter à `ReportControllerTest` un test confirmant que `GET /api/v1/reports/summary` renvoie
**404 Not Found** (l'endpoint n'existe plus) pour un utilisateur DAF, et que `/kpis` continue de
répondre 200 (déjà couvert par les tests existants — ne pas dupliquer). Le 404 prouve le retrait
sans casser l'accès KPI.

## Contraintes / hors périmètre

- **Aucune migration Flyway.**
- Ne PAS toucher `/kpis`, `/activity`, `/payment-cycle`, `/supplier/{id}/payments` (conservés pour
  câblage front par Antigravity).
- Ne PAS toucher `reportService.getDashboardKpis()` (utilisé ailleurs).
- Rien de frontend dans ce lot.

## Definition of Done

- [ ] `getSummary()` + `@GetMapping("/summary")` retirés de `ReportController`.
- [ ] Test : `GET /api/v1/reports/summary` → 404 (DAF).
- [ ] Gate backend `./mvnw test` ≥ 627/0/0/0 (baseline post-N10).
- [ ] `docs/KNOWN_ISSUES_REGISTRY.md` : PROB-130 (heredoc, jamais Edit).
- [ ] `docs/QA_AUDIT_EXHAUSTIF.md` : volet back de N8 marqué (alias retiré ; front → lot Antigravity) — non commité.
