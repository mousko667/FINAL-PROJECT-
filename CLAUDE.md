# OCT Invoice System — AI Agent Rules

> This file is the **primary directive** for any AI agent working on this project.
> Read this file **first**, every time, before writing any code.

---

## 1. Project Identity

**Name:** Système de Gestion des Factures Fournisseurs — OCT  
**Client:** Owendo Container Terminal (OCT)  
**Type:** Final Year Bachelor Project (enterprise-grade quality expected)  
**Stack:** Spring Boot 3.x · PostgreSQL 15 · React 18 + TypeScript · MinIO · Docker  
**Languages:** French (primary UI) + English (secondary) — all user-facing text must be bilingual  

---

## 2. Mandatory Reading Order

Before touching any file, read these documents in order:

1. `docs/PRD.md` — what the system does and why
2. `docs/WORKFLOW.md` — the OCT BAP process (business rules)
3. `docs/ARCHITECTURE.md` — technical decisions, layer rules
4. `docs/DATABASE.md` — schema, naming, constraints
5. `docs/API.md` — endpoint contract
6. `docs/CONVENTIONS.md` — code style, naming, patterns
7. `docs/TASKS.md` — current phase and next task
8. `docs/TESTING.md` — what tests are required for every feature
## CRITICAL — No autonomous planning

The development plan already exists in full in `docs/TASKS.md`.

- Do NOT create your own implementation plan
- Do NOT reorder, rename, or reinterpret tasks  
- Do NOT add tasks that don't exist in docs/TASKS.md
- Do NOT skip tasks because they seem "already done"
- Execute tasks EXACTLY as written, in the order they appear
- If a task is ambiguous, check docs/WORKFLOW.md and docs/ARCHITECTURE.md
  before making any assumption — never invent a solution
- If genuinely blocked, STOP and report the blocker — do not work around it

---

## 3. Core Constraints — Never Violate

### Architecture
- **Never** bypass the service layer from a controller
- **Never** put business logic in a controller or repository
- **Never** expose JPA entities directly from an endpoint — always use DTOs
- **Never** hardcode secrets, URLs, or credentials — always use `application.yml` + env vars
- **Always** wrap API responses in `ApiResponse<T>`
- **Always** use `@PreAuthorize` on every controller method
- **Always** use Flyway for every schema change — never `ddl-auto: create`

### Security
- **Never** log passwords, tokens, or bank details
- **Never** store bank details unencrypted — always AES-256 via `EncryptionUtil`
- **Always** validate file MIME type via Apache Tika before storage
- **Always** soft-delete financial records — never hard delete

### Code Quality
- **Always** add Javadoc on public service methods
- **Always** write unit tests for every service method
- **Always** write integration tests for every controller endpoint
- **Never** leave TODO comments in committed code — create a task instead
- **Always** handle exceptions via `GlobalExceptionHandler` — no raw try/catch in controllers
- **Never** use `System.out.println` — use `@Slf4j` logging

### Bilingual
- **Always** use `MessageSource` for user-facing strings — never hardcode French or English text
- Translation keys live in `messages_fr.properties` and `messages_en.properties`
- API error messages must be returned in the user's preferred language via `Accept-Language` header

---
## 4. File Creation Rules — Strict

### Only create files that are explicitly listed in docs/TASKS.md or directly requested.

**Never create unrequested files such as:**
- Extra markdown docs (NOTES.md, SETUP.md, CHANGELOG.md, TODO.md, SUMMARY.md…)
- Example or sample files not in the task
- Duplicate config files
- "Helper" scripts not in the task list
- README files until task P8-08 explicitly asks for it

**When completing a task, create ONLY:**
1. The exact files the task describes
2. The test file that corresponds to it
3. The Flyway migration if the task says "Create migration"

**If you think an extra file would be useful**, add it as a new task
at the bottom of the current phase in `docs/TASKS.md` instead of
creating it immediately. Let the developer decide.

**The only files you may always update without being asked:**
- `docs/TASKS.md` — mark tasks ✅
- `docs/MEMORY.md` — append discoveries
- `messages_fr.properties` and `messages_en.properties` — add new keys
---

## 5. Workflow Rules (BAP — Bon à Payer)

The core business process. Memorize this.

```
EMAIL FROM SUPPLIER
      ↓
Assistant Comptable receives email, enters invoice in system
      ↓
      BROUILLON (Draft)
      ↓ [submit]
      SOUMIS (Submitted)
      ↓ [start review]
      EN_VALIDATION_N1 (Under Review L1)
      ↓ [validate N1]
      ┌─────────────────────────────────┐
      │ Single-approval dept?           │
      │ → VALIDE → BON_A_PAYER → PAYE  │
      │ Two-approval dept?              │
      │ → EN_VALIDATION_N2 → VALIDE    │
      │   → BON_A_PAYER → PAYE         │
      └─────────────────────────────────┘
      
At any review stage: REJETE (Rejected) → back to SOUMIS for correction
Final: ARCHIVE
```

**Departments requiring TWO approval levels (N1 → N2):**
- Informatique: RSI → DSI
- Infrastructure: Resp. INFRA → Directeur INFRA
- Atelier / Direction Technique: Resp. Atelier → Directeur Technique

**Departments requiring ONE approval level (N1 only):**
- DRH, Direction Générale, Finance, Terminal, Communication & RSE, QHSSE

**There is NO supplier portal.** Suppliers send invoices by email. The system is internal only.  
**There are NO notifications to suppliers.** All notifications are internal.

---

## 6. Feedback Loop Protocol

After completing any feature or task:

1. **Self-check** — run through `docs/FEEDBACK_LOOP.md` checklist
2. **Test first** — run `./mvnw test` before marking a task done
3. **Update state** — mark the task in `docs/TASKS.md` as ✅ Done
4. **Log issues** — append any discovered issues to `docs/MEMORY.md` under "Known Issues"
5. **Never skip** — a task is not done until tests pass and the checklist is clean

---

## 7. When Uncertain

- Ambiguous business rule? → Check `docs/WORKFLOW.md` first, then `docs/PRD.md`
- Ambiguous technical decision? → Check `docs/ARCHITECTURE.md`
- Unknown endpoint shape? → Check `docs/API.md`
- Not sure which task is next? → Check `docs/TASKS.md` for the first unchecked item in the current phase

---

## 8. What Good Looks Like

A feature is complete when:
- [ ] Service method has Javadoc
- [ ] Unit tests cover happy path + at least 2 edge cases
- [ ] Integration test covers the endpoint with correct role
- [ ] French + English translation keys added
- [ ] Swagger `@Operation` annotation added to the endpoint
- [ ] Task marked ✅ in `docs/TASKS.md`
- [ ] No compiler warnings
- [ ] `./mvnw test` passes with 0 failures

---

## 9. Session Continuity

After every completed task, append a session checkpoint
to `docs/MEMORY.md` in this exact format:

```
## Session Checkpoint
**Date:** {date}
**Last completed task:** P{X}-{XX}
**Phase:** {phase number and name}
**Next task:** P{X}-{XX}
**Branch:** main
**Last commit:** {commit hash}
**Notes:** {any blockers, decisions, or context needed to resume}
```

This checkpoint must be written BEFORE committing the task.
Any new AI agent starting a session must read this checkpoint
first to know exactly where to resume.

---

## 10. Blocker Protocol

When stuck on any task, follow this exact protocol:

STOP immediately. Do not:
- Run diagnostic commands repeatedly
- Try multiple fixes in a loop
- Execute Start-Sleep or polling commands
- Attempt more than 2 fix attempts on the same error

Instead, report the blocker in this exact format:

---
BLOCKER REPORT
Current task: P{X}-{XX} — {task name from docs/TASKS.md}
Phase: {phase number and name}
Last successful task: P{X}-{XX}
Last commit: {hash}

What I was doing:
{one sentence describing the action that failed}

Exact error:
{paste the exact error message — nothing more}

What I tried:
1. {fix attempt 1 — one sentence}
2. {fix attempt 2 — one sentence}

What I need:
{one sentence describing what decision or information is needed}
---

Then STOP and wait for instructions.
Never run more than 2 fix attempts before issuing a blocker report.
Never run commands just to gather diagnostic information —
read existing log files instead.

---

## 11. Git Discipline

After completing each task AND verifying tests pass:

git add .
git commit -m "feat(phase-X): P{TASK-ID} — {short description}"

After completing a full Phase (all tasks ✅, exit criteria met):

git push origin main

Rules:
- NEVER commit if ./mvnw test fails
- NEVER commit .env file (already in .gitignore)
- Commit message must follow: type(scope): TASK-ID — description
- Push only at phase completion, not after every task