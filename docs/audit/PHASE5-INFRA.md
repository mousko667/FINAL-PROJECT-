# Phase 5 — Infrastructure & CI Audit

> Verified by direct inspection of `docker-compose.yml`, `.github/workflows/*.yml`, and `.env`. Builds on Open Item #1 from `BASELINE.md` (Phase 0), explicitly deferred to this phase.

---

## P5-01 — HAUTE: `docker-compose.yml`'s `postgres` service is orphaned — the backend connects to a host-native Postgres on port 5433 that docker-compose does not manage, and `backend.depends_on` doesn't even list `postgres`

**Severity: HAUTE** (re-formalizes `BASELINE.md` Open Item #1, deferred from Phase 0; `docker-compose up -d` as documented in the file's own header comment would NOT produce a working stack)

### The mismatch

`docker-compose.yml:13-33` defines a `postgres` service:
```yaml
postgres:
  image: postgres:18-alpine
  container_name: oct_postgres
  environment:
    POSTGRES_DB: ${DB_NAME:-oct_invoice_dev}
    POSTGRES_USER: ${DB_USER:-postgres}
    POSTGRES_PASSWORD: ${DB_PASSWORD:-dany}
  ports:
    - "${DB_PORT:-5432}:5432"
```

But the `backend` service (`docker-compose.yml:97-103`) is configured to connect elsewhere:
```yaml
environment:
  DB_HOST: ${DB_HOST:-host.docker.internal}
  DB_PORT: ${DB_PORT:-5433}
  DB_NAME: ${DB_NAME:-oct_invoice}
  DB_USER: ${DB_USER:-postgres}
  DB_PASSWORD: ${DB_PASSWORD:-dany}
```
And the actual `.env` (gitignored, present in this environment) confirms this is the LIVE configuration, not a stale default:
```
DB_HOST=host.docker.internal
DB_PORT=5433
```

Three independent symptoms of the same root cause:
1. **Port mismatch**: `postgres` service publishes container port 5432 to host port `${DB_PORT:-5432}` (i.e., host `5432` by default) — but `backend` connects to `host.docker.internal:${DB_PORT:-5433}` (i.e., host `5433`). Even using the SAME `DB_PORT` variable, the two defaults (`5432` vs `5433`) differ, AND `backend` deliberately targets the HOST (`host.docker.internal`) rather than the `postgres` container's service-name DNS (`postgres`), which `docker compose` would normally resolve.
2. **Database name mismatch**: `postgres` creates a DB named `${DB_NAME:-oct_invoice_dev}`; `backend` connects expecting `${DB_NAME:-oct_invoice}` — different default names (`oct_invoice_dev` vs `oct_invoice`).
3. **Missing `depends_on`**: confirmed via `docker compose config` — `backend.depends_on` lists only `minio` (`service_healthy`) and `minio_init` (`service_completed_successfully`). `postgres` is **absent from `backend`'s `depends_on`**, even though `extra_hosts: host.docker.internal:host-gateway` (`docker-compose.yml:93-94`) was specifically added to let the backend reach a database OUTSIDE the compose network — meaning the `postgres` service (lines 13-33) is not just misconfigured relative to `backend`, it is **architecturally unused** under the current topology.

### Root cause (per BASELINE.md investigation)
This is a deliberate but incomplete environment migration: at some point, `docker-compose.yml` was reconfigured to point `backend` at a host-side PostgreSQL (likely for local development convenience — running Postgres natively rather than in Docker). The `extra_hosts`, `DB_HOST=host.docker.internal`, `DB_PORT=5433`, and `DB_NAME=oct_invoice` changes were all made consistently on the `backend` service AND in `.env` — but the **committed `postgres` service definition (lines 13-33) was never removed or updated** to match, leaving it as dead configuration that contradicts the rest of the file's actual topology.

### Current observed state
`docker ps -a` shows all 6 containers (`oct_backend`, `oct_postgres`, `oct_frontend`, `oct_minio`, `oct_minio_init`, `oct_mailhog`) as `Exited` (3-5 days old). Per BASELINE.md, nothing currently listens on host port 5432 or 5433 — there is no running Postgres of either kind in this environment right now. `./mvnw test` (used throughout this audit) uses Testcontainers, which spins up its OWN ephemeral Postgres and is **unaffected** by this issue — this is why all backend test execution in Phases 0-4 worked despite this infra gap.

### Impact
Anyone following the `docker-compose.yml` header comment (`docker-compose up -d` → "Start all services") on a fresh clone, WITHOUT first setting up a host-native Postgres on port 5433 with database `oct_invoice`, would get a `backend` container that starts (passes `minio`/`minio_init` dependency) but **fails its healthcheck** (`wget --spider http://localhost:8080/actuator/health`) because Spring Boot cannot connect to the database — `host.docker.internal:5433` would refuse the connection, and the orphaned `oct_postgres` container (even if also started) listens on a different port/DB-name that `backend` never queries.

### Proposed fix (Phase 10)
Two valid resolutions — **requires a user/architectural decision**, not a unilateral code change:
- **Option A (Docker-only)**: Remove `backend`'s `host.docker.internal`/port-5433 overrides and `extra_hosts`; restore `DB_HOST: postgres`, `DB_PORT: 5432`, `DB_NAME: oct_invoice_dev` (or rename the `postgres` service's DB to `oct_invoice` to match `backend`'s current default — pick ONE canonical name); add `postgres: condition: service_healthy` to `backend.depends_on`.
- **Option B (host-Postgres, current intent)**: Remove the orphaned `postgres` service (lines 13-33) and its `postgres_data` volume entirely from `docker-compose.yml`; document in `docs/ARCHITECTURE.md §4.3` (deploy procedures) that a host-native PostgreSQL on port 5433 with database `oct_invoice` is a PREREQUISITE for `docker-compose up`, not something compose provides.

This audit does not pick one — flagging for Phase 9 (PLAN-CORRECTIONS.md) as a task requiring explicit direction before implementation.

---

## P5-02 — BASSE: `docker-compose.yml`'s `MINIO_SECRET_KEY` default (`dany`) does not match `MINIO_ROOT_PASSWORD` default (`dany1234`) — fresh-clone-only risk, does not affect this environment or CI

**Severity: BASSE** (only manifests if `.env` is missing or doesn't set `MINIO_SECRET_KEY`; this environment's `.env` sets `MINIO_SECRET_KEY=dany1234`, matching, so `minio_init` currently authenticates correctly; CI (`ci.yml:37,74` and `security-scan.yml:56`) hardcodes `dany1234` consistently for both, also unaffected)

`docker-compose.yml:43`:
```yaml
minio:
  environment:
    MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY:-dany1234}
```
`docker-compose.yml:66` (the `minio_init` bucket-creation step):
```yaml
minio_init:
  entrypoint: >
    /bin/sh -c "
    mc alias set local http://minio:9000 ${MINIO_ACCESS_KEY:-MinIO} ${MINIO_SECRET_KEY:-dany};
```
If `MINIO_SECRET_KEY` is unset (no `.env`, or `.env` doesn't define it): `minio` starts with root password `dany1234` (8 chars, its own default), but `minio_init` attempts `mc alias set` with password `dany` (4 chars, a DIFFERENT default) — authentication would fail, `minio_init` would exit non-zero, and (per `backend.depends_on: minio_init: condition: service_completed_successfully`) the `backend` container would never start.

### Proposed fix (Phase 10)
Trivial one-line fix: change `docker-compose.yml:66`'s `${MINIO_SECRET_KEY:-dany}` to `${MINIO_SECRET_KEY:-dany1234}` to match line 43's default. Bundle with P5-01's fix since both are in the same file and both are "fresh clone" correctness issues.

---

## Clean findings (✅)

- **`.github/workflows/ci.yml`**: fully self-contained — spins up its OWN `postgres:18-alpine` (port 5432, matching the COMMITTED `postgres` service's image/version) and `minio` services as GH Actions `services:` (not docker-compose), with consistent `MINIO_ROOT_PASSWORD`/`MINIO_SECRET_KEY` = `dany1234` throughout (lines 37, 74). `./mvnw test` (line 79) Flyway-migrates the full V1-V41 chain (including this session's new V41) against this fresh container — **CI is unaffected by the V41 addition and by P5-01/P5-02** (both are docker-compose-specific, not CI-specific). Re-confirms T4. ✅
- **`.github/workflows/security-scan.yml`**: triggers correctly after CI success on `main` (`workflow_run`, lines 6-10) or manually (`workflow_dispatch`); uses the same `postgres:18-alpine`/port-5432 pattern as `ci.yml`; OWASP ZAP baseline scan against a real running JAR with `.github/zap-rules.tsv` rules file. Re-confirms T4. ✅ — **One LOW-confidence observation, NOT filed as a finding** (no execution proof available, would require triggering an actual GH Actions run): `security-scan.yml` does not set `MINIO_BUCKET` env var or run an `mc mb` bucket-creation step (unlike `ci.yml:57-62`). If any code path hit by the ZAP scan requires the `oct-invoices` MinIO bucket to exist, this could cause scan-time errors — but `actuator/health` (the only readiness check, line 60) may not exercise MinIO at all, so this is speculative. Recommend a quick check during Phase 10 if time permits, but not elevated to a numbered issue without proof.
- **Image versions**: `postgres:18-alpine` consistent across `docker-compose.yml`, `ci.yml`, and `security-scan.yml`. ✅
- **`Dockerfile` multi-stage `runtime` target** referenced correctly by `docker-compose.yml:90` (`target: runtime`). ✅

---

## Summary for Phase 8 (ISSUES.md)

| ID | Severity | Summary | File:line | Proof |
|---|---|---|---|---|
| P5-01 | **HAUTE** | `docker-compose.yml`'s `postgres` service is orphaned (port/DB-name mismatch + missing from `backend.depends_on`); `docker-compose up -d` per the file's own usage comment would not produce a working backend on a fresh clone | `docker-compose.yml:13-33` (postgres svc), `:97-103` (backend env), `:119-123` (depends_on) | `docker compose config` confirms `depends_on` omits `postgres`; `.env` confirms live config uses host:5433/`oct_invoice`, contradicting the committed `postgres` service's container:5432/`oct_invoice_dev` |
| P5-02 | BASSE | `MINIO_SECRET_KEY` default mismatch between `minio` (`dany1234`) and `minio_init` (`dany`) — fresh-clone-only, breaks `minio_init` → blocks `backend` startup if `.env` doesn't set this var | `docker-compose.yml:43` vs `:66` | direct read; this environment's `.env` sets `MINIO_SECRET_KEY=dany1234` so currently masked |

**Phase 5 complete.** Both findings are docker-compose/local-dev-environment issues; CI and security-scan workflows are unaffected and remain valid (re-confirms T4).
