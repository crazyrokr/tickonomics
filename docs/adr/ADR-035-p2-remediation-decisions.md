# ADR-035: P2 Code-Review Remediation Decisions

## Status
Accepted

## Date
2026-06-22

## Context

The `docs/reviews/` code review tracked P2 (robustness, validation, API-typing) defects as "almost
entirely open" — 20 of 22 items unresolved. Resolving them required several choices that are
load-bearing for more than one file: how range queries are bounded, what the REST/web-socket security
posture is, where descriptive statistics live, how controller responses are typed, and how unimplemented
endpoints report themselves. This ADR records those decisions so future changes respect them. The item
IDs below cross-reference `docs/reviews/00-verification-report.md`.

## Decisions

### D1 — Bounded range queries with a configurable default cap (P-H7)

The `findBy*Between` family of repository queries was unbounded: an arbitrary time range could load an
unbounded number of rows. Fix is a **configurable default cap** (`persistence.query.default-limit`,
default 10 000) applied to every range query, plus opt-in `limit`/`offset` overloads and a
`PaginatedResponse<T>` envelope (used by `BacktestResultRepository.findPageByStrategyNameAndTimeBetween`).
A `WARN` is logged whenever a result hits the cap so truncation is never silent. Centralized in
`BoundedRangeQuery` + `QueryLimits`. Existing callers keep working (default cap is the only behaviour
change). Chosen over "explicit limit at every call site" to avoid churning every caller and over
"opt-in only" because that leaves the defect present for existing callers.

### D2 — Null-safe row mapping (P-H1)

JDBC `getDouble`/`getInt`/`getBoolean` return `0`/`false` for SQL NULL, silently corrupting nullable
metrics. Standardized on `RowMapperUtils.getNullable*` (`rs.getObject(...) != null ? rs.get*(...) : null`).
`CorrelationOutput.correlation`, `ZscoreSeries.rawValue`/`zScore` were boxed to `Double` so NULL can be
represented. This is the only schema-related typing change; money-as-`BigDecimal` remains ADR-033's scope.

### D3 — JSONB name search via a functional trigram index (P-H3)

`BacktestResultRepository` searched `strategy_config::text LIKE '%name%'` — a leading-wildcard scan that
no index can serve. Switched to `strategy_config->>'strategy' ILIKE :pattern` (the column holds
`{"strategy":"<name>"}`) backed by a GIN trigram index (`V40` migration, `pg_trgm`). Key-agnostic fallback
would be a trigram index on the whole text expression.

### D4 — Web security posture (W-H2..H7)

* **Catch-all:** the auth-enabled filter chain ends `.anyRequest().denyAll()` (was `permitAll()`). The
  auth-disabled branch stays `permitAll()` by design.
* **Method security:** `@EnableMethodSecurity` + a composed `@AuthenticatedWrite` meta-annotation
  (`@PreAuthorize("@securityProperties.authDisabled() or isAuthenticated()")`). The conditional SpEL keeps
  write endpoints open in the auth-disabled sandbox while requiring a principal in production — no lockout.
* **Headers:** `X-Content-Type-Options: nosniff` and `Referrer-Policy: strict-origin-when-cross-origin`
  added to both filter chains (Spring Security 7 lambda DSL).
* **Rate limiting:** dependency-free in-memory token-bucket `RateLimitGuard` on the kill-switch and
  close-position endpoints, returning HTTP 429 via `RateLimitExceededException` → `GlobalExceptionHandler`.
  **Single-host limitation:** the bucket is per-process; a multi-instance deployment needs a shared store
  (Redis). That is a deliberate, documented trade-off for the current single-host sandbox.
* **WebSocket origins:** `/ws/prices` and `/ws/signals` now use the REST CORS allow-list
  (`security.cors.allowed-origins`) via `setAllowedOrigins` instead of `"*"`.
* **WebSocket heartbeat:** a `@Scheduled` ping (`websocket.heartbeat-interval-ms`, default 30s) over all
  `BroadcastWebSocketHandler` sessions evicts half-open sockets.

### D5 — Typed controller responses aligned to the contract (W-C2)

Controllers no longer return `Map<String,Object>`. Response `record`s under `web/.../controller/dto/`
mirror the existing `openapi.yaml` component schemas (DemoPortfolio, DemoPosition, DemoTrade,
DemoCloseResult, IntersubjectivePath, …), so the contract stays the source of truth and no frontend type
regeneration was needed. `signal-quality` is the one intentional `Map` return: its contract schema is
`additionalProperties: true` (a genuinely free-form report). The operational kill-switch/safe-mode/
leverage-rotation endpoints are not in `openapi.yaml` (pre-existing gap); they are typed with records for
hygiene but remain out of the contract until promoted.

### D6 — Unimplemented endpoints return 501, never 200 + empty (W-H1)

The six `QuantController` stubs that returned `200` with empty data now return `HTTP 501` with a
`ProblemDetail`, so callers can distinguish "no data" from "not implemented". `getAuditPath` is the only
endpoint with real backing logic and returns the typed `IntersubjectivePathResponse`.

### D7 — Shared descriptive statistics (K-H4)

Statistics (mean, variance, std-dev, Sharpe) were duplicated across 8+ computation files.
`computation.util.StatisticsUtils` is the single source of truth, adopted at PairsTradingEngine,
RiskPremiumResidualMonitor, CrossModelValidator, WalkForwardValidator, UniverseAggregator,
ScheduledCalibrationTask, and FireflyWeightOptimizer. **Adoption is behaviour-preserving:** every prior
site used population variance, so `population*` variants match exactly; `sample*` variants (n-1) are
provided for future statistically-correct inference but were **not** retroactively substituted, because
that would change strategy outputs. `ForecastPersistenceService` retains inline **sample**-variance code
(a different convention plus a latent divide-by-zero on 2-tick input) and is intentionally left un-adopted
as a documented follow-up rather than risk a behaviour change in this pass.

### D8 — Phantom-liquidity and AUMF fixes (K-H1, K-H3)

`PhantomLiquidityService.computePli` returns a `NaN` sentinel (with a `WARN`) for non-positive
`totalVolumeAtBest` instead of the `Math.max(1.0, …)` floor that distorted low-volume PLI. The AUMF
`* 15` volatility-scaling factor is extracted to a named `VOLATILITY_ZSCORE_SCALE` constant with a
documented TODO to validate it against the volatilityRegime KPI range.

## Consequences

* Range queries are capped; callers that genuinely need all rows must use the paginated overload or raise
  `persistence.query.default-limit`.
* Auth-enabled deployments require a valid principal for every `/api/**` write; local dev is unaffected
  (`AUTH_DISABLED=true`).
* Rate limiting is best-effort and per-host; revisit before horizontal scaling.
* Transfer-entropy values (analytics, A-H5) shifted slightly due to the Miller-Madow correction;
  statistical-significance tests still pass.
