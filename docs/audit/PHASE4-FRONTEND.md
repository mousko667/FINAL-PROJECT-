# Phase 4 — Frontend Audit

> Verified by direct source inspection + a Node.js script comparing `t(...)` calls in `.tsx`/`.ts` against `src/i18n/en.json` and `src/i18n/fr.json` (file:line proof for all findings).

---

## P4-01 — MOYENNE: 94 translation keys (≈23% of all `t()` calls) are missing from BOTH `en.json` and `fr.json` — English-locale users see French (or, in some files, vice-versa) fallback text instead of localized text

**Severity: MOYENNE** (not a crash, not a raw-key-leak — but a direct violation of CLAUDE.md §1 *"all user-facing text must be bilingual"* across MFA setup, supplier registration/tracking, payments, archive, and GRN pages)

### Methodology
```js
// Flatten en.json and fr.json to dot-paths; extract every `t('key', ...)` / `t("key", ...)`
// call from src/**/*.{ts,tsx}; diff used-keys against both locale files.
```
Result:
- **EN/FR key-PARITY is perfect**: `en.json` and `fr.json` each have exactly **418 keys**, with **0 keys present in one but not the other**. ✅ (This was the FIRST check run, and it's clean — but it only proves the two files are in sync with EACH OTHER, not that they cover everything the UI actually calls.)
- **413 distinct `t(key, ...)` invocations** found in `src/**/*.{ts,tsx}`.
- **94 of those 413 keys (≈23%) do not exist in `en.json` (or `fr.json`)** — i.e., they were never added to either locale file, despite being actively called in components.
- **0 of the 94 missing keys are called with NO fallback argument** — i.e., none would render a raw key string (`t('mfa.title')` → `"mfa.title"`); all 94 use the `t(key, 'fallback text')` two-argument form, so a hardcoded fallback string always displays instead.

### The actual bug: hardcoded fallback strings are single-language, but get shown regardless of active locale

Two examples, both real, both reachable:

**`ProfilePage.tsx` (MFA setup section, 16 missing keys, fallbacks in French)**:
```tsx
// ProfilePage.tsx:175
{t('mfa.enterCode', '2. Entrez le code à 6 chiffres')}
// ProfilePage.tsx:79
setConfirmError(t('mfa.invalidOtp', 'Code invalide. Vérifiez votre application et réessayez.'))
```
`mfa.enterCode` and `mfa.invalidOtp` exist in **neither** `en.json` nor `fr.json`. When `i18n.language === 'en'`, `t()` falls through to the hardcoded French default — **an English-locale user configuring MFA sees French instructions** ("Entrez le code à 6 chiffres") on a security-critical setup screen.

**`SupplierRegisterPage.tsx` (supplier self-registration, fallbacks in English)**:
```tsx
// SupplierRegisterPage.tsx:67
setServerError(t('supplier.register.error', 'Registration failed. Please try again.'))
// SupplierRegisterPage.tsx:76-77
{t('supplier.register.successTitle', 'Registration Submitted!')}
{t('supplier.register.successMessage', 'Check your email to verify your account before logging in.')}
```
Same gap, opposite direction: when `i18n.language === 'fr'` (the **primary** language per CLAUDE.md §1: *"French (primary UI)"*), a French-speaking supplier registering for the first time sees **English** text — on the very first screen a new supplier encounters.

### Affected areas (94 keys grouped by feature)

| Prefix | Missing keys | Feature area | Fallback language observed |
|---|---|---|---|
| `supplier.*` | 33 | Supplier self-registration (`supplier.register.*`, 13 keys), email verification (`supplier.verify.*`, 5 keys), invoice tracking (`supplier.tracking.*`, 8 keys), portal nav (`supplier.portal.*`, 3 keys) | English (register/verify), mixed (tracking) |
| `mfa.*` | 16 | Entire MFA setup/status UI (`ProfilePage.tsx`) — QR scan instructions, OTP entry, status badges, error messages | French |
| `payments.*` | 7 | Payment history page (`PaymentsPage.tsx`) — section titles, empty states, remittance label | French |
| `archive.*` | 6 | Archive page (`ArchivePage.tsx`) — title, subtitle, search placeholder, empty/no-results states, retention note | French |
| `auth.*` | 6 | Login/password fields — `auth.email`, password-strength indicator (`auth.passwordStrength.{weak,fair,good,strong,hint}`) | — |
| `grn.*` | 6 | Goods Receipt Note pages — title, subtitle, create button, empty state, GRN number/date labels | — |
| `invoice.*` | 6 | Invoice detail — PDF export button, approval-step status labels (`invoice.step{Approved,Pending,Rejected}`), overdue badge, "no steps yet" empty state | — |
| `dashboard.*` | 4 | Dashboard quick-link cards (`dashboard.deptMatrix`, `manageAccounts`, `securityLogs`, `supplierRegistry`) | — |
| `admin.*` | 3 | Admin departments page — subtitle, currency column, matrix note | — |
| `nav.*` | 3 | Navigation sidebar items | — |
| `register.*` | 2 | (general registration, distinct from `supplier.register.*`) | — |
| `notifications.*` | 1 | — | — |
| `profile.*` | 1 | — | — |

### Proposed fix (Phase 10)
For each of the 94 keys: add an entry to BOTH `en.json` and `fr.json`, using the existing inline fallback string as the value for whichever language it's already written in, and translate it for the other language. Given the volume (94 keys × 2 files = 188 additions), this is a mechanical but non-trivial Phase 10 task — recommend grouping by the table above (13 sub-tasks by feature area) so each can be verified independently (build + visually check the affected page in both `fr`/`en` locales).

---

## P4-02 — REGRESSION-T6-02 CONFIRMED: Approval Delegation has a complete, working backend (T6/V40) but ZERO frontend — no UI exists to create, view, or revoke a delegation

**Severity: HAUTE** (re-confirms the finding flagged in `PHASE1-ARCHITECTURE.md` P1-02b as "partially resolved" — backend done, frontend missing)

`DelegationController` (`DelegationController.java`) exposes 3 endpoints:
```java
@PostMapping     // create a delegation
@GetMapping      // list delegations
@DeleteMapping("/{id}")  // revoke a delegation
```
A full-frontend search (`grep -rln "delegation\|delegate" frontend/src --include="*.ts" --include="*.tsx" -i`) returns **zero matches** — no API client method, no page, no component, no route, no nav item references delegation in any way.

**User-facing impact**: the V40 migration (`approval_delegations` table) and its service/controller layer (verified working end-to-end in T6) are entirely inert from a user's perspective — an approver who wants to delegate their approval authority (e.g., during vacation) has **no way to do so** through the application. This is a complete feature with no entry point — the OCT business requirement this serves (if any — cross-check in Phase 6 against `REQUIREMENTS-MATRIX.md`) is unmet on the frontend.

### Proposed fix (Phase 10)
Out of scope for a "fix" in the bug-fix sense — this is a **missing feature**, not a regression from working code. Recommend: (1) confirm in Phase 6 whether Approval Delegation is a scoped requirement; (2) if yes, scope a minimal UI (e.g., a "Delegations" section in `ProfilePage.tsx` or a dedicated page: list active delegations, a form to create one with target-user + date-range, a revoke button) as a new Phase 10 task with its own design/test/i18n-key additions.

---

## P4-03 — BASSE: TypeScript `strict` mode and `noImplicitAny` are both disabled

**Severity: BASSE** (no immediate bug, but reduces compile-time safety for an "enterprise-grade" project per CLAUDE.md §1)

`tsconfig.app.json:9-10`:
```json
"noImplicitAny": false,
"strict": false,
```
Also `noUnusedLocals: false` and `noUnusedParameters: false` (lines 21-22) — unused variables/parameters are not flagged by `tsc`.

### Proposed fix
Not recommended as a Phase 10 task — retrofitting `strict: true` onto an existing ~400-component codebase typically surfaces dozens-to-hundreds of new compiler errors (nullable handling, implicit `any` parameters) that would need individual fixes, far exceeding the scope of this audit's correction loop. Flagging as a **documented known limitation** for future work (`docs/KNOWN_ISSUES_REGISTRY.md`), not a Phase 10 item.

---

## P4-04 — BASSE: MFA QR code `<img>` has no `alt` attribute; QR payload (containing the TOTP `otpauth://` URI) is sent to a third-party service

**Severity: BASSE** (single image, admin/self-service settings page only — but touches both accessibility and a privacy consideration)

`ProfilePage.tsx:150-151`:
```tsx
{/* QR Code via Google Charts API — otpauth URI embedded */}
<div className="flex-shrink-0">
  <img
    src={`https://api.qrserver.com/v1/create-qr-code/?data=${encodeURIComponent(setupData.qrCodeUrl)}&size=160x160&margin=8`}
```
This is the **only `<img>` tag in the entire frontend** (`grep -rn "<img" src --include="*.tsx"` → 1 match), and it has no `alt` attribute. A screen-reader user setting up MFA would not know this image is a QR code to scan.

Separately (informational, not a new finding requiring a fix — likely an accepted trade-off for a student project, but worth recording): the `setupData.qrCodeUrl` (an `otpauth://totp/...?secret=...` URI containing the user's TOTP secret) is passed as a URL query parameter to `api.qrserver.com`, a third-party QR-rendering API. The TOTP secret is encrypted at rest server-side (per P2-04/T1), but is transmitted in the clear (as part of a URL, likely logged by the third-party service and any intermediate proxies/CDNs) to generate the QR image client-side. A self-hosted QR generation library (e.g., `qrcode.react`, already common in this stack) would avoid this exposure.

### Proposed fix (Phase 10)
1. Add `alt={t('mfa.qrCodeAlt', 'QR code for MFA setup — scan with an authenticator app')}` (note: this would be a NEW, 95th i18n key — group with P4-01's Phase 10 work).
2. (Optional, larger scope) Replace the third-party QR API with a client-side library — flag in `docs/KNOWN_ISSUES_REGISTRY.md` as a future improvement rather than a Phase 10 task, since it changes a dependency and rendering approach beyond a simple `alt` fix.

---

## Clean findings (✅)

- **EN/FR key parity**: `en.json` and `fr.json` have identical key sets (418/418, 0 discrepancies in either direction) — the two locale files are perfectly mirrored. ✅ (The gap is in keys missing from BOTH, not an EN/FR sync issue — see P4-01.)
- **`ErrorBoundary`** (`src/components/ErrorBoundary.tsx`) exists and is wired into `AppRoutes.tsx`. ✅
- **Empty states**: handled inline per-page (e.g., `ArchivePage.tsx` has `archive.empty`/`archive.noResults` branches) — no dedicated `EmptyState` component exists, but the pattern is present and functional (the i18n keys themselves are part of the P4-01 gap, but the conditional-rendering logic is correct). ✅ (logic), see P4-01 (text)
- **`tsconfig.json`** project-references setup (`tsconfig.app.json` + `tsconfig.node.json`) is structured correctly per Vite conventions. ✅

---

## Summary for Phase 8 (ISSUES.md)

| ID | Severity | Summary | File:line | Proof |
|---|---|---|---|---|
| P4-01 | MOYENNE | 94/413 (`≈23%`) translation keys missing from both `en.json`/`fr.json`; hardcoded single-language fallbacks shown regardless of active locale | `ProfilePage.tsx` (16 mfa.* keys), `SupplierRegisterPage.tsx` (13 supplier.register.* keys), + `archive`/`payments`/`grn`/`invoice`/`dashboard`/`admin`/`nav`/`auth` (61 more) | Node.js diff script: 94 keys in source, absent from both 418-key locale files |
| P4-02 | **HAUTE** | Approval Delegation (T6/V40, backend complete) has zero frontend — confirms REGRESSION-T6-02 | `DelegationController.java` (3 endpoints), `frontend/src/**` (0 references to "delegation") | `grep -rln "delegation" frontend/src -i` → no matches |
| P4-03 | BASSE | TS `strict`/`noImplicitAny`/`noUnusedLocals`/`noUnusedParameters` all `false` | `tsconfig.app.json:9-10,21-22` | direct read |
| P4-04 | BASSE | MFA QR `<img>` missing `alt`; TOTP secret sent to third-party QR API | `ProfilePage.tsx:150-151` | only `<img>` in frontend, 0 `alt=` |

**Phase 4 complete.**
