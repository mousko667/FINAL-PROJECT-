# FEEDBACK LOOP — Self-Correction Protocol

> Run this checklist after completing every task before marking it ✅.
> The AI agent must work through this list — never skip sections.

---

## Level 1 — After Every File Written

Before moving to the next file, run through:

- [ ] **Compiles?** No syntax errors, no unresolved imports
- [ ] **Follows naming conventions?** (see `docs/CONVENTIONS.md §1`)
- [ ] **No hardcoded strings?** User-facing text uses `MessageSource` keys
- [ ] **No secrets?** No passwords, tokens, or keys in code
- [ ] **Logging safe?** No sensitive data in log statements
- [ ] **Exception handling?** Uses specific exception classes, not raw `RuntimeException`
- [ ] **DTOs only?** Controller returns DTO, not raw entity
- [ ] **No unrequested files created?** Only files listed in the task exist, no extra docs, notes, or helpers

---

## Level 2 — After Every Service Method Written

- [ ] **Has Javadoc?** `@param`, `@return`, `@throws` documented
- [ ] **Has unit test?** At minimum: happy path + 1 failure case
- [ ] **Follows single responsibility?** Method does ONE thing
- [ ] **Transaction correct?** `@Transactional` where DB writes occur
- [ ] **Side effects async?** Events published via `applicationEventPublisher`, not direct calls
- [ ] **Role check present?** Service verifies actor is allowed to perform this action

---

## Level 3 — After Every Feature Complete (Controller + Service + Tests)

Run the full feature checklist:

```
□ Unit tests written for all service methods
□ Integration tests cover all endpoints × roles
□ All invalid transitions tested (WorkflowException expected)
□ All wrong-role attempts tested (403 expected)
□ French translation keys added to messages_fr.properties
□ English translation keys added to messages_en.properties
□ Swagger @Operation annotation on controller method
□ Flyway migration added if schema changed
□ docs/TASKS.md task marked ✅
□ ./mvnw test runs with 0 failures
□ No new compiler warnings
```

---

## Level 4 — After Every Phase Complete

### Backend health check
```bash
./mvnw clean test
# Expected: BUILD SUCCESS, 0 failures, 0 errors
# If failures: fix before proceeding to next phase

./mvnw test jacoco:report
# Check: target/site/jacoco/index.html
# Expected: ≥80% line coverage, ≥75% branch coverage
```

### API contract check
```bash
# Start app
./mvnw spring-boot:run

# Verify Swagger UI accessible
curl http://localhost:8080/api/v1/v3/api-docs | jq .info

# Verify all expected endpoints exist in the spec
```

### Docker check
```bash
docker-compose up --build
# Expected: all services healthy, no crash loops
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

### Translation check
```bash
# Verify no missing translation keys
grep -r "messageSource.getMessage" src/main/java | \
  awk -F'"' '{print $2}' | sort | uniq > /tmp/used_keys.txt

grep -oP '(?<=^)\S+(?==)' src/main/resources/messages_fr.properties | \
  sort > /tmp/fr_keys.txt

diff /tmp/used_keys.txt /tmp/fr_keys.txt
# Expected: no diff — all used keys exist in FR file
```

---

## Level 5 — Self-Correction When Tests Fail

When `./mvnw test` produces failures:

### Step 1 — Categorize the failure
```
A) Compilation error      → Fix imports, types, method signatures first
B) Test logic error       → Read the test, understand what it expects
C) Business rule mismatch → Re-read docs/WORKFLOW.md before changing code
D) Security test failure  → Re-read docs/ARCHITECTURE.md §5 (Role Hierarchy)
E) Transaction error      → Check @Transactional placement
F) State machine error    → Re-read docs/WORKFLOW.md §3 (State Machine)
```

### Step 2 — Fix in isolation
- Fix ONE failure at a time
- Run the single failing test after each fix: `./mvnw test -Dtest=ClassName#methodName`
- Never "fix" a test by weakening its assertions

### Step 3 — Regression check
- After fixing, run the full test suite: `./mvnw test`
- A fix that breaks other tests is not a fix

### Step 4 — Root cause note
- If the bug was caused by a misunderstanding of the business rules → update `docs/MEMORY.md` with the clarification
- If the bug was caused by a wrong architectural decision → update `docs/ARCHITECTURE.md` with the correction

---

## Level 6 — When Uncertain About Business Rules

Priority order for resolving uncertainty:

1. `docs/WORKFLOW.md` — authoritative business rules
2. `docs/PRD.md` — what the system should do
3. `docs/MEMORY.md` — previously resolved decisions
4. The OCT workflow Excel file (`Workflow_validation_BAP.xlsx`) — original source
5. Ask the developer (if all else unclear)

**Never guess on business rules.** An incorrect workflow implementation is worse than a missing feature.

---

## Continuous Improvement Log

After any session where something went wrong, append a retrospective note here:

```
[DATE] [Issue] [Root Cause] [How Fixed] [Rule Added to CONVENTIONS.md?]
```

*(This section grows over the project lifetime)*
