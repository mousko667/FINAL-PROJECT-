# OCT Invoice System — AI Agent Rules

> This file is the **primary directive** for any AI agent working on this project.
> Read this file **first**, every time, before writing any code.

---

## 1. Project Identity

**Name:** Système de Gestion des Factures Fournisseurs — OCT  
**Client:** Owendo Container Terminal (OCT)  
**Type:** Final Year Bachelor Project (enterprise-grade quality expected)  
**Stack:** Spring Boot 3.4 · Java 21 · PostgreSQL 18 · React 19 + TypeScript · MinIO · Flyway · Docker  
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
7. `docs/TASKS.md` — living implementation roadmap: current status by module + open gaps
8. `docs/TESTING.md` — what tests are required for every feature

> **Sources of truth (business scope, fixed):** `docs/Project requirements.txt` (14 modules),
> `docs/REQUIREMENTS-MATRIX.md` (departmental matrix, ref. 25627), `docs/OCT_System_Briefing.md`
> (project identity & rules). **Ground truth for *what is built*:** the code itself, recorded in
> `docs/TASKS.md` (§A Open Gaps · §B Out of Scope · §C status by module).

## Planning discipline

`docs/TASKS.md` is the single living roadmap. The original phase plan (`P{X}-{XX}`) and the
former `docs/COMPLIANCE_MATRIX.md` were merged into it on 2026-06-22.

- Work from `docs/TASKS.md §A Open Gaps` for what remains; respect `§B Out of Scope`.
- Do NOT silently invent scope: anything not in the Project Requirements / Briefing needs a decision.
- Keep `docs/TASKS.md` current: when a gap is closed or a module status changes, update it.
- If a requirement is ambiguous, check `docs/WORKFLOW.md` then `docs/PRD.md` / the Briefing
  before making any assumption — never invent a solution.
- If genuinely blocked, STOP and report the blocker — do not work around it.

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

### Only create files that the task at hand needs, or that the developer directly requested.

**Never create unrequested files such as:**
- Extra markdown docs (NOTES.md, SETUP.md, CHANGELOG.md, TODO.md, SUMMARY.md…)
- Example or sample files not needed by the task
- Duplicate config files
- "Helper" scripts not needed by the task

**When implementing a feature/gap, create ONLY:**
1. The exact files the feature needs
2. The test file that corresponds to it
3. The Flyway migration if a schema change is required (next contiguous version number)

**If you think an extra file would be useful**, note it as a new item under
`docs/TASKS.md §A Open Gaps` instead of creating it immediately. Let the developer decide.

**The only files you may always update without being asked:**
- `docs/TASKS.md` — update module status / close open gaps
- `docs/MEMORY.md` — append discoveries
- `docs/KNOWN_ISSUES_REGISTRY.md` — log every bug found and fixed (MANDATORY)
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
      ↓ [assign_aa — AA control]
      EN_CONTROLE_AA (Under AA Control)
      ↓ [assign_reviewer]
      EN_VALIDATION_N1 (Under Review L1)
      ↓ [validate N1]
      ┌─────────────────────────────────┐
      │ Single-approval dept?           │
      │ → VALIDE → BON_A_PAYER → PAYE  │
      │ Two-approval dept?              │
      │ → EN_VALIDATION_N2 → VALIDE    │
      │   → BON_A_PAYER → PAYE         │
      └─────────────────────────────────┘
      
At any review stage (EN_CONTROLE_AA / EN_VALIDATION_N1 / EN_VALIDATION_N2):
  REJETE (Rejected) → back to SOUMIS for correction
Final: ARCHIVE
```

**Departments requiring TWO approval levels (N1 → N2):**
- Informatique: RSI → DSI
- Infrastructure: Resp. INFRA → Directeur INFRA
- Atelier / Direction Technique: Resp. Atelier → Directeur Technique

**Departments requiring ONE approval level (N1 only):**
- DRH, Direction Générale, Finance, Terminal, Communication & RSE, QHSSE

**⚠ CORRECTION (verified 2026-06-06) :** A supplier portal DOES exist at `/supplier/*`.
Suppliers can self-register, log in, submit their own invoices, and track status.
Supplier notifications (submission confirmed, rejected, paid) ARE implemented via email + in-app.
The CLAUDE.md original statement was incorrect and has been superseded by actual implementation.

---

## 6. Feedback Loop Protocol

After completing any feature or task:

1. **Self-check** — run through `docs/FEEDBACK_LOOP.md` checklist
2. **Test first** — run `./mvnw test` before marking a task done
3. **Update state** — update the relevant module status / close the gap in `docs/TASKS.md`
4. **Log issues** — append any discovered issues to `docs/MEMORY.md` under "Known Issues"
5. **Log bugs** — if you fixed a bug, add it to `docs/KNOWN_ISSUES_REGISTRY.md` BEFORE committing
6. **Never skip** — a task is not done until tests pass and the checklist is clean

---

## 7. When Uncertain

- Ambiguous business rule? → Check `docs/WORKFLOW.md` first, then `docs/PRD.md`
- Ambiguous technical decision? → Check `docs/ARCHITECTURE.md`
- Unknown endpoint shape? → Check `docs/API.md`
- Not sure what to work on next? → Check `docs/TASKS.md §A Open Gaps`

---

## 8. What Good Looks Like

A feature is complete when:
- [ ] Service method has Javadoc
- [ ] Unit tests cover happy path + at least 2 edge cases
- [ ] Integration test covers the endpoint with correct role
- [ ] French + English translation keys added
- [ ] Swagger `@Operation` annotation added to the endpoint
- [ ] `docs/TASKS.md` updated (module status / open gap closed)
- [ ] No compiler warnings
- [ ] `./mvnw test` passes with 0 failures

---

## 9. Session Continuity

After every completed task, append a session checkpoint
to `docs/MEMORY.md` in this exact format:

```
## Session Checkpoint
**Date:** {date}
**Last completed work:** {module / gap ID, e.g. "G1 CI pipeline" or "M5 #4"}
**Next work:** {module / gap ID}
**Branch:** {branch}
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
Current work: {module / gap ID — e.g. "G1 CI pipeline"}
Last successful work: {module / gap ID}
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

## 12. Living Documentation Rule — MANDATORY

> This rule applies to every agent, every session, every project that uses these instruction files.

**Every time a problem is encountered AND resolved, the agent MUST:**

1. Add the problem to `docs/KNOWN_ISSUES_REGISTRY.md` (before committing the fix) with:
   - Root cause (WHY it happened, not just what happened)
   - Exact solution (which files, which changes)
   - Preventive rule (how to never reproduce it)

2. If the problem reveals a missing rule, update the relevant doc:
   - Bug about entity naming → add to `docs/CONVENTIONS.md`
   - Bug about security → add to `docs/ARCHITECTURE.md §5` and `CLAUDE.md §3`
   - Bug about deployment → add to `docs/ARCHITECTURE.md §4.3`
   - Bug about testing → add to `docs/TESTING.md`
   - Bug about workflow logic → add to `docs/WORKFLOW.md`

3. If the problem was caused by an INCORRECT rule in these docs, CORRECT the rule.
   Do not preserve wrong information because it was the original instruction.

**Why this matters:** These files are used as reference instructions for future projects.
Each bug fixed and documented here prevents the same bug from happening again.
An instruction file that does not reflect real-world experience is less useful than one that does.

**Format for the update:**
```
// In the relevant section, add:
// ⚠ LESSON LEARNED (PROB-NNN, date): {short description}
// See docs/KNOWN_ISSUES_REGISTRY.md for full context.
```

---

## 13. Known Bug Prevention Rules (from KNOWN_ISSUES_REGISTRY.md)

These rules were derived from real bugs encountered on this project. Apply them preemptively.

### Frontend — Auth & Routing
- **NEVER** initialise `user: null` in Redux if a JWT token exists in localStorage → always rehydrate via `GET /profile` at app startup (see PROB-001)
- **ALWAYS** create separate route guards per role category: `ProtectedRoute` (staff), `SupplierRoute` (suppliers) — never one guard for all (see PROB-002)
- **ALWAYS** use `RoleGuard` with `fallback={null}` in navigation items, `PageRoleGuard` with error UI on page-level guards (see PROB-004)

### Backend — Entities & Mapping
- **NEVER** name boolean fields `boolean isXxx` with Lombok — Lombok generates `isIsXxx()` (double prefix). Name it `boolean xxx` → Lombok generates `isXxx()` correctly (see PROB-003)
- **ALWAYS** compile + test immediately after any field rename — use class-specific replacements, not global regex (see PROB-010)

### Backend — Flyway
- **NEVER** modify the content of a migration that has already been applied. Its checksum is locked in `flyway_schema_history`. Any change requires a NEW migration with the next version number (see PROB-009)

### Backend — iText 8 PDF
- **NEVER** use `Cell.setBorderColor(color)` — it does not exist in iText 8. Use `Cell.setBorder(new SolidBorder(color, width))` (see PROB-012)
- **NEVER** use `BigDecimal.valueOf(BigDecimal)` — use `.longValue()` or `.doubleValue()` to convert (see PROB-013)

### Frontend — Error Display
- **ALWAYS** pass backend error messages through `t(key)` before display — the backend may return i18n keys as error strings (see PROB-006)
- **ALWAYS** add a silent error handler on WebSocket/STOMP connections to prevent console spam (see PROB-007)

### Security
- **NEVER** allow a user to change their own email via a simple PUT — email changes require confirmation flow (see PROB-008)

### Infrastructure — Docker Deploy
- Frontend deploy procedure: `npm run build` → `docker cp dist/. oct_frontend:/usr/share/nginx/html/` → `docker exec oct_frontend nginx -s reload`
- Backend deploy procedure: `mvnw.cmd -DskipTests package` → `docker cp target/*.jar oct_backend:/app/app.jar` → `docker restart oct_backend`
- NEVER assume `docker restart` updates static files in nginx — the files must be explicitly copied (see PROB-011)

### Audit Log
- **ALWAYS** check the actual endpoint and field names when an audit/list page shows empty — the issue is usually a wrong endpoint or a wrong field name, not an empty database (see PROB-005)

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

### Branching — one topic per branch (⚠ LESSON LEARNED, 2026-07-09)

> Rule added after `chore/sanitize-docs-migrations` accumulated **236 commits** spanning two
> unrelated efforts (audit fixes + a full UI redesign). A `chore/`-named branch that ends up
> carrying features is a lie about its own content and produces an unreviewable history.

- **`main` is the single reference branch.** New work starts from a **short, well-named branch
  scoped to ONE topic**: `feat/<topic>`, `fix/<topic>`, `chore/<topic>` — and the name must keep
  matching what the branch actually contains for its whole life.
- **Do NOT reuse a long-lived catch-all branch** across unrelated features. When a topic is done,
  merge it back to `main` and start a fresh branch for the next topic.
- **Prefer fast-forward merges into `main`** (`git merge --ff-only`) when the branch is a linear
  descendant — no merge commit, clean history. Merging into `main` is an outward-facing action:
  **only merge/push to `main` with the user's explicit go-ahead** for that session.
- After a branch is fully merged into `main`, **delete it** (local + remote) so `main` stays the
  only reference and the topic branch can't silently diverge later.
- Solo dev: uncommitted tree changes are the user's own prior-session work — keep one-commit-one-topic,
  don't quarantine them.


### Phase 9 — Supplier, MFA, Matching & Integration Constraints

#### Supplier Domain
- **Never** store supplier bank details unencrypted — always AES-256 via `EncryptionUtil`
- **Never** hard-delete suppliers — soft delete only (`deleted_at`)
- `Supplier` entity is now the authoritative source for supplier data;
  flat text fields on `Invoice` (`supplierName`, `supplierEmail`, etc.)
  remain nullable for backward compatibility but new invoices MUST link via `supplier_id` FK
- Supplier status lifecycle: `PENDING_VERIFICATION → ACTIVE → SUSPENDED`
- Only `ROLE_ADMIN` can change supplier status
- `ROLE_SUPPLIER` users can only see their own invoices and their own profile

#### MFA
- MFA is **mandatory** for roles: `ROLE_DAF`, `ROLE_ADMIN`,
  `ROLE_VALIDATEUR_N1_*`, `ROLE_VALIDATEUR_N2_*`
- Login flow when MFA enabled: first call returns `mfa_required: true` +
  a short-lived `pre_auth_token` (5 min TTL); second call submits OTP
  and returns the full JWT
- **Never** expose the raw TOTP secret in any API response after setup confirmation
- Lock account after 5 consecutive failed OTP attempts; only `ROLE_ADMIN` can unlock

#### Three-Way Matching
- Matching is triggered automatically when an invoice with a `purchaseOrderId`
  is submitted (BROUILLON → SOUMIS)
- If matching status is `MISMATCH`, invoice cannot proceed past `SOUMIS`
  without a manual override recorded by `ROLE_DAF` **only** — `ROLE_ADMIN` has
  no financial access and cannot override a matching mismatch (SoD, see
  `InvoiceController.overrideMatchingMismatch`, `@PreAuthorize("hasRole('DAF')")`)
- Tolerance thresholds are stored in DB (`matching_config` table), not hardcoded
- `ThreeWayMatchingResult` is append-only — no updates, no deletes

#### Webhooks
- Webhook payloads must be signed with HMAC-SHA256 using the stored secret
- Delivery failures retry 3 times with exponential backoff (5s, 25s, 125s)
- Webhook delivery log is append-only — never modify or delete records
- Only `ROLE_ADMIN` can register, update, or delete webhooks
- Webhooks fire on events: `INVOICE_SUBMITTED`, `INVOICE_APPROVED`,
  `INVOICE_REJECTED`, `INVOICE_PAID`