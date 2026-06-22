# Hors-Scope Assumé — OCT Invoice System

> **Canonical home of the assumed out-of-scope list (kept because `docs/REQUIREMENTS-MATRIX.md`
> Module 12 points here by name).** The same content is also summarized in the living roadmap at
> `docs/TASKS.md §B Out of Scope`. If the two ever diverge, this file remains the authoritative
> Module-12 exclusion record; `TASKS.md §B` is the convenience copy.

> Created during Phase 6 (Requirements Coverage Audit), per `docs/REQUIREMENTS-MATRIX.md` Module 12's
> explicit instruction: items that are largely absent and represent enterprise-integration scope
> beyond a Bachelor's Final Year Project should be documented here as "hors-scope assumé" rather
> than logged as bugs — confirmed with the project owner on 2026-06-12.

---

## Module 12 — Integration (partial scope exclusion)

Of the 12 items listed for Module 12 in `docs/REQUIREMENTS-MATRIX.md`, **9 are declared
hors-scope assumé** for this project:

| # | Item (from REQUIREMENTS-MATRIX.md) | Status |
|---|---|---|
| 1 | Dashboard de config d'intégration | Hors-scope assumé |
| 2 | Connexion ERP (SAP, Oracle, MS Dynamics) | Hors-scope assumé |
| 3 | Intégration système d'approvisionnement | Hors-scope assumé |
| 4 | Connexion logiciel comptable | Hors-scope assumé |
| 5 | Intégration bancaire pour paiements | Hors-scope assumé |
| 6 | Intégration GED (externe) | Hors-scope assumé |
| 7 | Interface de config API | Hors-scope assumé |
| 10 | Config de planning de sync | Hors-scope assumé |
| 12 | Interface de test de connexion | Hors-scope assumé |

**Rationale**: these items describe connectors to specific named third-party enterprise systems
(SAP, Oracle, MS Dynamics, external accounting/banking/GED platforms) and the generic
configuration/scheduling/testing tooling that would surround such connectors. None of these
have any backend or frontend implementation, and building even one such connector (with
authentication, data mapping, sync scheduling, and error-resolution UI) is a substantial
project on its own — well beyond the scope of a Bachelor's invoice-management FYP. No
`docs/PRD.md`/`docs/WORKFLOW.md` requirement formally mandates a specific external-system
connector, so this exclusion does not contradict the cahier des charges.

### NOT excluded — items 8, 9, 11 (Webhooks & Integration Status)

The remaining 3 items (8: Gestion des webhooks, 9: Monitoring du statut d'intégration,
11: Log d'erreurs + résolution) are **explicitly NOT covered by this scope exclusion** and
remain tracked as a normal finding (**REQ-22**, `docs/audit/REQUIREMENTS-COVERAGE.md` Module 12).

The distinction: unlike items 1-7/10/12, a substantial, correctly-architected backend for
webhooks ALREADY EXISTS (`WebhookController`, `IntegrationStatusController`,
`WebhookService` — HMAC-SHA256 signing, 3-retry exponential backoff per CLAUDE.md §9,
admin-gated, append-only delivery log). This is a GENERIC integration mechanism (not tied
to any named third-party system) that webhooks subscribers (internal tooling, future
connectors, etc.) could already use. The gap is purely that no frontend UI was ever built
to manage it — the same "backend-complete/frontend-absent" pattern as REQ-08, REQ-11,
and P4-02 elsewhere in this audit, and therefore treated the same way: as a real (if
low-urgency) gap, not a scope exclusion.

---

## How to extend this file

If a future audit phase identifies another module/item that is largely absent and
represents scope beyond this project's cahier des charges, add a new section here
following the same format: list the excluded items, give the rationale, and explicitly
call out anything in the same module that is NOT excluded (if applicable).
