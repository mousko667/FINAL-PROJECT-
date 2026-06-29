# Phase 3 — Performance & Data Audit

> Verified by direct code inspection (file:line) — N+1 queries, indexes, pagination, transaction boundaries, Flyway sequence consistency.

---

## P3-01 — MEDIUM: `GET /api/v1/purchase-orders` (no `supplierId`) loads ALL purchase orders, unpaginated

**Severity: MOYENNE** (no immediate crash; degrades linearly as the table grows — a multi-year OCT deployment could accumulate thousands of POs)

`PurchaseOrderController.listPurchaseOrders` (`PurchaseOrderController.java:90-102`):
```java
@GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')")
public ResponseEntity<ApiResponse<List<PurchaseOrderDTO>>> listPurchaseOrders(
        @RequestParam(required = false) UUID supplierId) {
    List<PurchaseOrder> pos = supplierId != null
            ? purchaseOrderService.listBySupplier(supplierId)
            : purchaseOrderService.listAll();   // <-- unbounded
    ...
}
```

`PurchaseOrderService.listAll()` (`PurchaseOrderService.java:126-129`):
```java
@Transactional(readOnly = true)
public List<PurchaseOrder> listAll() {
    return purchaseOrderRepository.findAll();
}
```

`findAll()` is Spring Data's unfiltered `SELECT * FROM purchase_orders` with no `LIMIT`/`Pageable`. Every row is loaded into memory and mapped to a DTO in a single response. Contrast with `InvoiceController.listInvoices` (`InvoiceController.java:70-87`), which correctly uses `Page<Invoice>`/`PagedResponse<T>` with `page`/`size`/`sort` params (`findAllWithFilters`, `InvoiceRepository.java:55`) — the PO list endpoint should follow the same pattern.

**Note**: `GoodsReceiptController.listGRNs` (`GoodsReceiptController.java:51-60`) was also flagged for the same shape (`@RequestParam(required = false) UUID purchaseOrderId`) but is **NOT an issue** — when `purchaseOrderId` is null it returns `List.of()` (empty), not all GRNs. It is always scoped to one PO by design. ✅ Clean.

### Proposed fix (Phase 10)
Add `Pageable`/`PagedResponse<PurchaseOrderDTO>` to `listPurchaseOrders`, mirroring `InvoiceController.listInvoices`. Update `PurchaseOrderService.listAll()` to `Page<PurchaseOrder> listAll(Pageable pageable)` using `purchaseOrderRepository.findAll(pageable)`.

---

## P3-02 — HIGH: `invoices.supplier_id` has no index, despite being used as a filter in 4 separate queries — including the supplier portal's own invoice list (the endpoint fixed in P2-01)

**Severity: HAUTE** (every supplier-scoped invoice query does a full table scan on `invoices`; this directly affects `GET /api/v1/supplier/invoices`, the supplier portal's primary endpoint, now that P2-01 makes it actually reachable)

`invoices.supplier_id` was added by `V14__update_invoices_supplier_fk.sql:5` (`ADD COLUMN IF NOT EXISTS supplier_id UUID REFERENCES suppliers(id)`) — **no accompanying `CREATE INDEX`**. Cross-checked all 16 `CREATE INDEX` statements across `V*.sql`: `invoices` has indexes on `status`, `department_id`, `created_at`, `(status, created_at)`, `purchase_order_id`, and `matching_status` (all from `V12`/`V21`) — but **never `supplier_id`**.

This column is used as a filter predicate in:
1. `InvoiceRepository.findAllWithFilters(..., supplierId, Pageable)` (`InvoiceRepository.java:55-63`) — used by the **supplier portal's invoice list** (`SupplierPortalController` → `InvoiceService` → this query), the exact endpoint whose `409` was fixed in P2-01.
2. `countInvoicesByStatusForSupplier` (`InvoiceRepository.java:85-86`): `WHERE i.supplier.id = :supplierId GROUP BY i.status`
3. `countInvoicesByMatchingStatusForSupplier` (`InvoiceRepository.java:88-89`): same pattern, `GROUP BY i.matchingStatus`
4. `findNextExpectedPaymentDateForSupplier` (`InvoiceRepository.java:91`): `WHERE ... i.supplier.id = :supplierId AND i.status = 'BON_A_PAYER' AND NOT EXISTS (...)`

All 4 are reachable from the supplier portal dashboard (`getDashboard()`/profile-stats endpoints) and now execute on **every supplier login** thanks to the P2-01 fix making the portal flow actually complete. As the `invoices` table grows, each of these becomes a sequential scan filtered by `supplier_id`.

### Proposed fix (Phase 10)
New migration `V42__add_invoices_supplier_id_index.sql`:
```sql
CREATE INDEX IF NOT EXISTS idx_invoices_supplier_id ON invoices(supplier_id);
```
(V41 is the next-available version after this session's P2-01 fix; V42 is next.)

---

## P3-03 — LOW: Flyway migration sequence has an undocumented gap (V36, V37, V38 do not exist)

**Severity: BASSE** (Flyway does not require contiguous version numbers — V35 → V39 applies cleanly with no functional impact — but the gap is undocumented and its origin is untraceable)

Full version sequence on disk: `V1`...`V35`, then `V39`, `V40`, `V41` (V41 created this session for P2-01). **V36, V37, V38 do not exist** and have never existed:
```
$ git log --all --diff-filter=D --name-only | grep -E "V3[6-8]__"
(no output — these files were never created and then deleted in git history)
```

P1-02b's prior note speculated this gap was *"already covered by V17-19"* — this claim could **not be substantiated**: `V17`/`V18`/`V19` (purchase orders, GRNs, three-way-matching tables) predate `V35` numerically and have no logical connection to the V35→V39 boundary (which sits between bank-detail encryption and `active_sessions`/`approval_delegations`). The "covered by V17-19" explanation appears to be an unverified assumption from the prior audit cycle, not a documented fact — **flagging it as incorrect** per the Living Documentation Rule (CLAUDE.md §12.3: "If the problem was caused by an INCORRECT rule in these docs, CORRECT the rule").

### Proposed fix (Phase 11)
No functional fix needed (Flyway handles non-contiguous versions natively, confirmed working since `V39`/`V40`/`V41` already apply successfully in test runs throughout this audit). Update `docs/ARCHITECTURE.md`/`docs/KNOWN_ISSUES_REGISTRY.md` to remove the unsubstantiated "covered by V17-19" claim and instead record: *"V36-38 are reserved/skipped version numbers of unknown origin — confirmed to never have existed in git history. No functional impact. Do not attempt to retroactively create V36-38."*

---

## P3-04 — HIGH: Webhook delivery — unused timeout config + unconfigured `RestTemplate` (infinite timeout) + blocking `Thread.sleep` retry chain inside an `@Async`/`@Transactional` service

**Severity: HAUTE** (a single unresponsive webhook subscriber URL can occupy an async worker thread indefinitely, across up to 3 recursive retries with `Thread.sleep` delays of 5s/25s/125s between attempts — `Executor` thread-pool exhaustion risk under concurrent webhook events)

### The dead config property
`WebhookService.java:46-47`:
```java
@Value("${webhook.delivery.timeout.seconds:5}")
private int deliveryTimeoutSeconds;
```
This field is declared and injected (default `5` seconds) but **never read anywhere else in the class** — confirmed via `grep -n "deliveryTimeoutSeconds" WebhookService.java` returning only the declaration line. A timeout was clearly *intended* (the property name, default value, and `@Value` annotation all signal deliberate design) but was never wired to the actual HTTP call.

### The unconfigured RestTemplate
`WebConfig.java:10-13`:
```java
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}
```
A bare `new RestTemplate()` uses `SimpleClientHttpRequestFactory` with **default connect/read timeouts of `0` (infinite)**. This bean is used at `WebhookService.java:161`:
```java
var response = restTemplate.exchange(webhook.getUrl(), HttpMethod.POST, request, String.class);
```
— no per-call timeout override either. A webhook URL that accepts the TCP connection but never responds will hang this call **forever**.

### The blocking retry chain
`deliverWithRetry` (`WebhookService.java:131-175`) is `private`, recursive, called from the `@Async`-annotated public entry point (`WebhookService.java:117-121`):
```java
@Async
public void deliverEvent(...) {
    ...
    deliverWithRetry(webhook, eventType, payloadJson, 0);
}
```
Inside `deliverWithRetry`:
- Line 142: `Thread.sleep(delayMs)` where `delayMs ∈ {5000, 25000, 125000}` (`RETRY_DELAYS_MS`, line 49) — a **blocking sleep** on the async worker thread, between each retry.
- Line 161: the unbounded HTTP call described above.
- On `RestClientException` OR non-2xx status, recurses into another attempt (lines 169, 173).

**Worst case for one event delivery to one webhook**: attempt 1 (hangs forever on a dead URL) → never even reaches the `Thread.sleep`/retry, because `restTemplate.exchange()` itself never returns. If the URL is merely *slow* (e.g., responds after 200s), the full chain is: `exchange()` (200s) → retry → `sleep(5s)` → `exchange()` (200s) → retry → `sleep(25s)` → `exchange()` (200s) → retry → `sleep(125s)` → final failure recorded. **≈755 seconds occupying one async thread**, per webhook, per event.

The class is `@Transactional` at the class level (`WebhookService.java:38-39`). Whether the `@Async` proxy preserves/propagates this transaction onto the async thread depends on Spring AOP proxy ordering (`@Async` typically creates a new thread without an inherited transaction context, but `@Transactional(propagation = REQUIRES_NEW)`-style new-transaction-per-call is the default for a class-level `@Transactional` if no transaction is active) — **regardless of whether a DB transaction/connection is held for the full duration**, the async executor's thread pool itself (bounded, e.g. Spring Boot's default `SimpleAsyncTaskExecutor` or a configured `ThreadPoolTaskExecutor`) can still be exhausted by enough concurrent slow deliveries, since each occupies a thread for up to ~755s.

### Proposed fix (Phase 10)
1. Configure `RestTemplate`'s underlying request factory with explicit connect/read timeouts, wired from the existing `webhook.delivery.timeout.seconds` property:
   ```java
   @Bean
   public RestTemplate restTemplate(@Value("${webhook.delivery.timeout.seconds:5}") int timeoutSeconds) {
       var factory = new SimpleClientHttpRequestFactory();
       factory.setConnectTimeout(timeoutSeconds * 1000);
       factory.setReadTimeout(timeoutSeconds * 1000);
       return new RestTemplate(factory);
   }
   ```
   This makes `deliveryTimeoutSeconds` actually load-bearing (or remove it from `WebhookService` if the bean-level config supersedes it — avoid having the same timeout configured in two places).
2. Re-evaluate whether `Thread.sleep`-based retry is acceptable for an `@Async` method, or whether retries should be rescheduled via `@Scheduled`/a delay queue instead — out of scope for a minimal fix, but worth a `docs/KNOWN_ISSUES_REGISTRY.md` note for future work given the 755s worst case even with a 5s per-call timeout (3 × 5s calls + 5s+25s+125s sleeps = 170s — better, but still long for one async slot).

---

## Clean findings (✅)

- **`InvoiceController.listInvoices`** (`InvoiceController.java:70-87`): correctly paginated via `Page<Invoice>`/`PagedResponse<InvoiceDTO>`, with `page`/`size`/`sort`/filter params. ✅
- **N+1 on `Invoice.items`/`Invoice.documents`**: `InvoiceMapper.toDto` (`InvoiceMapper.java:12-14`) does **not** map `items` or `documents` — listing invoices does not trigger LAZY-collection access. ✅
- **`Invoice.supplierName` aggregation** (`InvoiceRepository.java:79`, `findTopSuppliersByAmount` GROUP BY `i.supplierName`): initially flagged as risky given CLAUDE.md §9's claim that flat supplier fields are "nullable for backward compatibility" — but `Invoice.supplierName` is actually `@Column(nullable = false, length = 255)` (`Invoice.java:70-71`). The field is always populated; the aggregation is safe. **CLAUDE.md §9's "nullable" claim is itself slightly imprecise** (flagging for Phase 11 doc correction, low priority — the field being NOT NULL is the *safer* of the two states, so this is a documentation-accuracy issue, not a bug).
- **FK index coverage** (excluding `invoices.supplier_id`, see P3-02): `users.supplier_id`, `purchase_orders.supplier_id`, `purchase_order_items.purchase_order_id`, `goods_receipt_notes.purchase_order_id`, `three_way_matching_results.purchase_order_id`, `invoices.purchase_order_id`, `webhooks.created_by`, `audit_logs.user_id`, `notifications.user_id`, `active_sessions.user_id` — all indexed. ✅
- **`@OneToMany` fetch types**: `Invoice.items`, `Invoice.documents`, `User.userRoles` default to LAZY (no `FetchType.EAGER` found anywhere in the codebase — `grep -rn "FetchType.EAGER"` returns zero matches). `GoodsReceiptNote.items` and `PurchaseOrder.items` are explicitly `FetchType.LAZY`. ✅
- **`PurchaseOrderController.getPurchaseOrder(id)`** (`PurchaseOrderController.java:85-87`) uses `getPurchaseOrderWithItems(id)` — an explicit eager-load-with-items pattern for the single-resource case, appropriate since the detail view needs `items`. ✅

---

## Summary for Phase 8 (ISSUES.md)

| ID | Severity | Summary | File:line | Proof |
|---|---|---|---|---|
| P3-01 | MOYENNE | `GET /api/v1/purchase-orders` (no `supplierId`) returns ALL POs, unpaginated | `PurchaseOrderController.java:90-102`, `PurchaseOrderService.java:126-129` | `listAll()` → `purchaseOrderRepository.findAll()`, no `Pageable` |
| P3-02 | **HAUTE** | `invoices.supplier_id` has no index; used in 4 queries incl. supplier portal dashboard (now reachable post-P2-01) | `V14__update_invoices_supplier_fk.sql:5` (no index), `InvoiceRepository.java:55-63,85-86,88-89,91` | grep of all 16 `CREATE INDEX` statements: `supplier_id` absent for `invoices` table |
| P3-03 | BASSE | Flyway V36-38 gap is undocumented/unexplained; prior "covered by V17-19" claim unsubstantiated | `src/main/resources/db/migration/` (V35 → V39 jump) | `git log --all --diff-filter=D` shows V36-38 never existed |
| P3-04 | **HAUTE** | Webhook delivery: `deliveryTimeoutSeconds` config unused, `RestTemplate` has infinite default timeout, recursive retry with `Thread.sleep` (5s/25s/125s) on `@Async` thread — worst case ~755s/delivery | `WebhookService.java:46-47` (unused), `WebConfig.java:10-13` (no timeout), `WebhookService.java:131-175` (retry chain) | grep confirms `deliveryTimeoutSeconds` read nowhere; `new RestTemplate()` with no factory args |

**Phase 3 complete.**
