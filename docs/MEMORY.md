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
