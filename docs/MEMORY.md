# MEMORY — AI Agent Persistent Context

> This file is updated by the AI agent after each work session.
> It accumulates knowledge that must persist across sessions.
> Append, never overwrite.

---

## Project Snapshot

**Current Phase:** P0 — Not started  
**Last Completed Task:** None  
**Last Updated:** Initial creation  

---

## Critical Context (Always Keep in Mind)

### What makes this project unique
1. **No supplier portal** — invoices arrive by email, entered manually by ASSISTANT_COMPTABLE
2. **BAP = Bon à Payer** — the core business process, not just a generic workflow
3. **Department-driven approval** — the department on the invoice determines approval chain
4. **Dual language** — French is the primary language for OCT users, English secondary
5. **9 departments** — 6 single-level, 3 dual-level (INFO, INFRA, TECH)
6. **DAF is always the final approver** regardless of department

### Sensitive design decisions already made
- `InvoiceStatus` enum uses French names (`BROUILLON`, `SOUMIS`, etc.) stored in DB as strings
- `Department` table stores approval config — not hardcoded, admin-configurable
- Reference number format: `FAC-{YYYY}-{NNNNN}` — DB sequence resets annually
- Bank details encrypted with AES-256 via `@Convert` on entity field
- File storage: MinIO with SHA-256 integrity check on every download
- Events are async (`@Async`) — a failed email never rolls back a transaction

---

## Resolved Decisions

| Decision | Chosen Option | Reason |
|---|---|---|
| Workflow engine | Spring State Machine | Lightweight, Spring-native |
| Department config | DB table | Admin-manageable without code deploy |
| Notifications | Spring Application Events + @Async | Decoupled, failure-safe |
| Entity mapping | MapStruct | Compile-time, zero reflection |
| DB migrations | Flyway | Reproducible schema |
| File storage | MinIO | Self-hosted, S3-compatible |

---

## Known Issues / Blockers

*(Append issues here as they are discovered)*

---

## Completed Phases

*(Append completed phases here)*

---

## Discovered Constraints

*(Append any new constraints discovered during development)*

---

## Notes from Previous Sessions

*(Append session notes here)*

---

## Session checkpoints

After each completed task, append a `## Session Checkpoint` block here **before** committing that task (see `CLAUDE.md` §9). When resuming work, read the **most recent** checkpoint first.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-01
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-02
**Branch:** main
**Last commit:** b8227ccc2e88bfe4da802bcc6b4df52969a32aab
**Notes:** Added Flyway V7 (`approval_steps`) and V8 (`invoice_status_history`) per `docs/DATABASE.md`; indexes included on same migrations.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-02
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-03
**Branch:** main
**Last commit:** 90587c28a9c35799e3d57c2097e6b0af692f9233
**Notes:** `ApprovalStep` + `ApprovalStepStatus` under `domain.workflow.model`; unique constraint on `(invoice_id, step_order)`.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-03
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-04
**Branch:** main
**Last commit:** 32a942a4c47d2aa8682513e9d65f156f41166a49
**Notes:** `InvoiceStatusHistory` entity maps `from_status` / `to_status` as strings (aligns with `InvoiceStatus` enum names in DB).

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-04
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-05
**Branch:** main
**Last commit:** 01006e4233908ca740cd070bdddfdb0995da1951
**Notes:** `InvoiceEvent` enum in `domain.invoice.statemachine` (all events from WORKFLOW §3).

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-05
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-06
**Branch:** main
**Last commit:** 6e527ca895506ea470cefc18b4f2f528d7f91300
**Notes:** `StateMachineConfig` + `@EnableStateMachineFactory("invoiceStateMachineFactory")`; N1 routing uses extended state key `department` (`Department.requiresN2`).

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-06
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-07
**Branch:** main
**Last commit:** 7a8e3c465f88fc8ae161259bdd26369d97e4ae90
**Notes:** `DepartmentTransitionGuard` + `WorkflowExtendedStateKeys.DEPARTMENT`; guards delegate to `Department.isRequiresN2()` from extended state.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-08
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-09
**Branch:** main
**Last commit:** 7a8e3c465f88fc8ae161259bdd26369d97e4ae90
**Notes:** Implemented `InvoiceStateMachineService` and `InvoiceStateChangeListener` to persist state and history. Tests are passing.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-09
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-10
**Branch:** main
**Last commit:** pending
**Notes:** Implemented `ApprovalService` with deadline creation logic (3 business days per step) and DAF steps always as step_order 3.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-10
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-11
**Branch:** main
**Last commit:** pending
**Notes:** Implemented `ApprovalController` and related DTOs for recording validation decisions. Tests pass.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-11
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-12
**Branch:** main
**Last commit:** pending
**Notes:** Added `submit` and `resubmit` endpoints to `InvoiceController`.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-12
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-13
**Branch:** main
**Last commit:** pending
**Notes:** Implemented `DocumentRequiredGuard`, `RejectionReasonGuard`, and `RoleMatchGuard` and wired them into `StateMachineConfig`.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-13
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-14
**Branch:** main
**Last commit:** pending
**Notes:** Added i18n keys for workflow actions (assign, validate, bon_a_payer, reject, submit, resubmit).

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-14
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-15
**Branch:** main
**Last commit:** pending
**Notes:** Added tests for InvoiceStateMachineService verifying all valid and invalid transitions and guards. E2E state transitions fully covered.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-15
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P3-16
**Branch:** main
**Last commit:** pending
**Notes:** Added ApprovalServiceTest covering assignReviewer, validateN1, validateN2, bonAPayer, reject, and deadline computation with their respective role checks.

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P3-19
**Phase:** 3 — Workflow Engine (BAP State Machine)
**Next task:** P4-01
**Branch:** main
**Notes:** Completed integration testing for the ApprovalController (P3-16 to P3-19). Handled proper MockMvc setup with @EntityGraph for UserRoles to avoid LazyInitializationException, matched State Machine context variables, and moved explicit role checks to ApprovalServiceImpl to ensure API endpoints cleanly return 403 Forbidden instead of 400 Bad Request on workflow role enforcement. Phase 3 is fully ✅. Ready for Phase 4 (Notifications).

## Session Checkpoint
**Date:** 2026-03-29
**Last completed task:** P4-01
**Phase:** 4 — Notifications
**Next task:** P4-02
**Branch:** main
**Notes:** Created V9__create_notifications.sql Flyway migration script mapping to the DATABASE.md specification. Validated via `mvnw test`.
