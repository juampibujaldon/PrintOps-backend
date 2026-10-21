# Performance work - results

Branch `feature`, not committed. Measured with Hibernate Statistics (`getPrepareStatementCount`,
`getEntityLoadCount`) on a throwaway Postgres 16 (Testcontainers), and `EXPLAIN ANALYZE` on 300k rows.
Seed: 1 workspace, 5 printers, 20 orders (3 checklist + 3 parts + 2 photos each), 40 spare parts, 30 rules.

## How the tests are isolated
- Tests run under profile `test` (`src/test/resources/application-test.yaml`) against a Testcontainers
  Postgres (`IntegrationTestBase`). The datasource URL in the profile is an invalid placeholder and a
  `@BeforeAll` guard fails if the URL ever contains `:5432/` or `/printops`. The dev database is never touched.
- The baseline "existing test" (`DemoApplicationTests`) was NOT run in its original form, because it
  connects to the real dev DB. The first baseline is the same test re-pointed at the container (green)
  plus `compileJava compileTestJava` on the untouched code (green).
- JDK 21 installed with winget (Temurin 21.0.12). Build and tests were run with `JAVA_HOME` pointing at it.

## Before / after (SQL statements per call)

| Path | Before | After | Notes |
|---|---|---|---|
| `GET /api/orders` (20 orders) | 6 | 5 | `assignedTo` fetched in the main query. Printer is only used by id, so no printer fetch needed. Entities loaded unchanged (203): children still load per batch. |
| `GET /api/orders/{id}` | 6 | 5 | `@EntityGraph(assignedTo)` on `findByIdAndWorkspaceId` |
| `GET /api/parts?lowStock=true` (40 parts, 10 low) | 1 stmt, **40 entities** | 1 stmt, **10 entities** | filter/search/category in SQL (`LIKE ... ESCAPE`, wildcards stay literal) |
| `GET /api/printers/{id}/history` size=5 of 20 | 11 stmts, **144 entities / 40 collections** | 11 stmts, **39 entities / 10 collections** | page in DB (`Page` + count). Statement count is flat: count query replaced the "load all"; the win is rows and entities loaded, which grow with history size. |
| History `size=1000` with 120 orders | 120 items returned | 100 items (cap) | cap = 100; `size<=0` still means 20 |
| `GET /api/quotes` (10 quotes, 5 printers) | 2 | 1 | `LEFT JOIN FETCH q.printer` |
| `evaluateAllRules` (30 triggered rules, 2 techs) | 242 | 212 | technician lookup once per workspace instead of once per rule (-29), printers joined in the rules query (-1). One extra statement per rule was avoided with direct `@Modifying` updates (a plain `save()` on the detached rule would have added a SELECT per rule and cancelled the gain). |
| `changeStatus -> COMPLETED` (3 low-stock parts) | 23 | 20 | managers queried once, not per low-stock part (-2); assignedTo fetched in the order query (-1) |
| Metrics `avgResolutionHours` | loads `(createdAt, updatedAt)` for every completed order in the period | 1 aggregate row | SQL: `AVG(TRUNC(EXTRACT(EPOCH ...)/60))/60` (same minute truncation as before). Postgres-specific native query. |
| Printer MTBF | loads every corrective date | 1 scalar | SQL window `LAG`, same per-interval day truncation. Postgres-specific native query. |

## Index effect (EXPLAIN ANALYZE, 300k orders, 600k parts, 200k notifications, Postgres 16)
Run with `PERF_EXPLAIN=true ./gradlew test --tests '*IndexExplainIT'` (skipped otherwise). Execution time:

| Query | Without new indexes | With |
|---|---|---|
| list, workspace + status, newest first | 12.3 ms (seq scan) | 0.05 ms |
| history page (printer, ws) | 11.9 ms | 0.42 ms |
| history count (printer, ws) | 11.4 ms | 0.30 ms |
| rule has open order | 13.2 ms | 0.03 ms |
| `order_parts` for 50 orders | 44.4 ms | 0.34 ms |
| notifications of one user | 7.1 ms | 0.30 ms |

Timings are from a container on a dev laptop with a warm cache: use them for ratios, not absolute values.

## Behaviour that changed (all intended)
- **Scheduler isolation**: before, a rule failing at the DB level made the shared transaction rollback-only
  and `evaluateAllRules` threw `UnexpectedRollbackException`, losing every rule in the batch (reproduced in
  `SchedulerIsolationIT`). Now each rule runs in its own `REQUIRES_NEW` transaction.
- **Cache eviction after commit**: `@CacheEvict` removed from `OrderService` and `SparePartService` and replaced by
  `DashboardCache.invalidate()` (event + `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`).
  Newly evicting: scheduler-created orders, wear-created corrective orders (`QuoteService`), `createPrinter`.
- `spring.jpa.open-in-view=false`, gzip, long cache on `/uploads/orders/**` and `/uploads/printers/**`.

## Files changed
Entities (indexes): `MaintenanceOrder` (3), `OrderPart`, `OrderPhoto`, `OrderChecklistItem`, `StatusHistory`,
`StockMovement`, `Notification`, `Printer`, `Quote`, `MaintenanceRule`, `RefreshToken` (2), `TokenBlacklist`, `LoginAudit`.
Repositories: `MaintenanceOrderRepository` (`findForList`, `findHistory`, `averageDaysBetweenCorrectives`,
`averageResolutionHours`, graph on `findByIdAndWorkspaceId`; removed 7 now-unused derived queries and 2 Java-side queries),
`SparePartRepository.search`, `UserRepository.findFirstByRoleAndWorkspaceIdOrderByIdAsc`, `QuoteRepository` (fetch printer),
`MaintenanceRuleRepository` (`findActiveWithPrinter`, `markTriggered`, `markAlertSent`).
Services: `OrderService`, `SparePartService`, `PrinterHistoryService`, `GlobalMetricsService`, `MaintenanceRuleService`,
`QuoteService`, `PrinterService`, new `DashboardCache`. Config: `application.yaml`, `WebMvcConfig`. Build: `build.gradle.kts` (Testcontainers).

## Tests added (`src/test/java/com/printops/demo`)
`support/IntegrationTestBase`, `perf/QueryCountIT` (8), `perf/BehaviorIT` (10, characterization, green before and after),
`perf/SchemaIndexesIT` (4), `perf/SchedulerIsolationIT` (1), `perf/DashboardCacheIT` (6), `perf/DashboardCacheCommitIT` (3),
`perf/HttpOptimizationsIT` (4 + the manual `IndexExplainIT`). Red before the changes: 19 of the 36 tests that existed at that point; after: 37 run green, 1 manual test skipped (`./gradlew clean build` green).

## Not done / caveats
- `PrinterService.updateStatus` / `updateNextMaintenanceDate` do not evict: the cached payload contains only printer id and name,
  so neither changes it. There is no printer rename endpoint. `QuoteService.applyWear` does not evict either: it only changes
  `totalPrintingHours`, which the dashboard does not show. Evicting there would only lower the hit rate.
- `server.compression.min-response-size` is not honoured for these endpoints: Spring flushes the body, so the response is chunked with
  no `Content-Length`, and Tomcat compresses chunked responses regardless of the minimum. Large JSON is gzipped (tested); tiny bodies are too.
- Index creation relies on `ddl-auto: update` adding missing named indexes on the existing dev database. Not verified against a
  populated pre-existing schema, only on fresh schemas. `idx_rules_active` (boolean) is low-selectivity; a partial index would be better
  but needs a migration. `idx_orders_rule_status` was added on top of the requested list (scheduler hot path).
- Upload folders: only orders/printers get the one-year cache. `uploads/quotes/PRE-<id>.pdf` keeps the old behaviour because it is overwritten under the same name.
- `LOWER()` in the spare-part search follows the database collation; for non-ASCII letters on a "C" locale DB it may differ from Java's `toLowerCase`.
- Native queries (MTBF, average resolution) are Postgres-only.
- Not verified: behaviour of the React Native client; all endpoints were exercised only through the tests listed above.

## Recommendations (out of scope)
IDENTITY to SEQUENCE (enables JDBC insert batching, e.g. the per-rule inserts), caching user details in `JwtAuthFilter`
(a DB hit per request; security trade-off on revocation), Flyway, a paginated contract for `/api/orders` and `/api/parts`,
a notification and `login_audit` purge policy, a partial index on active rules.
