# Elimination Plan

**Date:** 2026-06-14
**Input:** `docs/reviews/00-verification-report.md` (verified findings only)
**Convention:** All fixes add Given-When-Then unit tests first (regression safety for untested code, per `CLAUDE.md`). Architectural changes are recorded in one ADR (see §6).
**Branch:** `feature/production-infrastructure`

---

## 1. Guiding principles

1. **Silent no-ops first.** Defects that produce plausible-but-empty output (K-C2, I-C1/C2/C3/C4, F-E1, A-M2) are prioritized above defects that merely compute wrong numbers, because no alarm will ever fire on them.
2. **One shared fix over many local fixes.** Money-as-double (P-C1/W-C4/K-M1), duplicated statistics (K-H4/K-C4), and metric instrumentation (I-H4) are each executed as a single workstream, not per-file patches.
3. **Test before change.** Every P0/P1 fix lands with a G-W-T test that fails on the old code. New behavior is never untested.
4. **No security-by-default regression.** Any change to auth/security config must keep `auth-disabled=true` non-default in non-local profiles.
5. **Migrations are additive and idempotent.** New SQL migrations never mutate existing rows destructively; views use `CREATE OR REPLACE`.

---

## 2. Phase summary

| Phase | Theme | Items | Exit criteria |
|---|---|---|---|
| **P0** | Silent correctness failures + open-by-default security | 13 | No production path returns empty output while appearing to succeed; auth defaults to on. |
| **P1** | Data integrity, money precision, observability plumbing | 14 | Transactions on writes; money is exact; dashboards have data. |
| **P2** | Robustness, validation, API contract typing | 16 | NULLs handled; DTOs typed; queries paginated; defects from reviews substantively closed. |
| **P3** | Hygiene, consistency, hardening polish | remaining | Lint fails builds; naming consistent; leftover low-severity items cleared. |

Effort estimates are T-shirt (S ≈ ½ day, M ≈ 1–2 days, L ≈ 3–5 days) for a developer already familiar with the module. They cover code + tests, not review/merge.

---

## 3. P0 — Silent correctness failures & security defaults (do first)

> These items either silently corrupt trading/backtest decisions or leave the system open by default. They are independent enough to parallelize across the 7 module teams, except as noted.

### P0-1 · `BacktestEngine` never feeds strategies usable input — **K-C2** (L)
The most damaging bug in the codebase: every backtest returns neutral signals.
- **Fix:** Introduce an `IndicatorComputer` that, per bar, emits the indicator map each strategy reads (`rsi`, `price`, `emaFast`, `emaSlow`, `upperBand`, `lowerBand`, `macd`, …). `BacktestEngine` calls it before `strategy.compute(input, ctx)`. Keys must match the union of keys read by the 25 strategies (enumerate via grep of `input.get("...")` across `computation/equity/`).
- **Test (G-W-T):** *Given* a daily-returns series with a clear RSI overbought condition, *when* `BacktestEngine` runs `RSIOscillatorStrategy`, *then* at least one non-neutral `AlphaSignal` is emitted (current code: zero). Add one regression test per strategy family (momentum/reversion/breakout/oscillator).
- **Dependency:** none. **Blocker for trusting any backtest output.**

### P0-2 · `computeSharpeFromWeights` uses weights not returns — **K-C1** (S)
- **Fix:** Delete `computeSharpeFromWeights`; route `doCalibration`/`perturbOptimize` to the existing correct `computeSharpeRatio(double[][], double[])`. If a return series is not available at the call site, thread one in from the calibration data set.
- **Test:** *Given* two weight vectors with identical weight stats but different asset-return series, *then* the Sharpe values differ (current code: identical).

### P0-3 · Fixed-income butterfly weights violate duration-neutrality — **K-C4** (S)
- **Fix:** Replace `wShort/wLong` with `wShort = (dL - dM) / (dL - dS)`, `wLong = (dM - dS) / (dL - dS)` (derived from `wShort·dS + (-1)·dM + wLong·dL = 0` with `wShort + wLong = 1`). Guard `dL == dS`.
- **Test:** *Given* arbitrary `dS < dM < dL`, *then* `wShort·dS - dM + wLong·dL` is within 1e-9 of 0 (current code: nonzero for generic inputs).

### P0-4 · Ingestion silent no-ops — **I-C1 / I-C2 / I-C3 / I-C4** (M)
Four independent fixes; one PR per source.
- **I-C1 Shiller discard:** pipe `shillerAdapter.toCdm(row)` through `writer.writeRate(...)` exactly as VIX/oil/gold backfills do. Test: *given* a parsed Shiller CSV, *then* `writeRate` is invoked once per row.
- **I-C2 Finnhub WS auth:** append `?token=` + URL-encoded `apiKey` to `wsUrl` before `wsClient.execute(...)`. Test: *given* an apiKey, *then* the connect URL contains `token=<apiKey>`.
- **I-C3 API-key typo:** change `monitor.finnub.api-key` → `monitor.finnhub.api-key` in `FinnhubEquityClient` and `FinnhubNewsClient`. Test: assert `@Value` resolves the yml-declared key (Spring `@ApplicationContextRunner`).
- **I-C4 FRED/NYFed namespaces:** add `fred:` and `nyfed:` sections to `application.yml` (or align `@Value` to `monitor.*`). Test: *given* `FRED_API_KEY` env, *then* `FredClient.apiKey` is non-empty.

### P0-5 · `CdmTick.equals` reference equality — **C-C1** (S)
- **Fix:** Override `equals`/`hashCode` using `Arrays.equals`/`Arrays.hashCode` on `conditions` (keep the defensive clone). Or change the component to `List<Integer>` and drop the manual clone.
- **Test:** *given* two `CdmTick` built from `new int[]{1,2}` via separate construction, *then* they are `equals` and share a `hashCode` (current code: not equal).

### P0-6 · CDM option/PUT silent misclassification — **C-C2** (S)
- **Fix:** `"CALL".equals(raw.optionType()) ? CALL : "PUT".equals(...) ? PUT : throw new IllegalArgumentException(...)`. Normalize case first.
- **Test:** *given* `"call"`, `"PUT"`, and `"JUNK"`, *then* call→CALL, put→PUT, junk→throws.

### P0-7 · French factor reversal data loss — **C-C3** (M)
- **Fix:** Add `stRev`/`ltRev` to `FrenchFactorRow`; parse from the CSV columns Ken French publishes; populate in `FrenchFactorCdmAdapter`. If the source does not provide them, throw rather than emit NaN silently.
- **Test:** *given* a row with reversal fields, *then* they are non-NaN; *given* a row without, *then* an exception is raised.

### P0-8 · GARCH forecast omits alpha — **A-C1** (S)
- **Fix:** `sigma2 = omega + sigma2 * (sum(alpha_coeffs) + sum(beta_coeffs))` (use the already-computed `persistence`). Convergence target becomes `omega/(1-persistence)`.
- **Test:** *given* a fitted GARCH(1,1) with alpha>0, *then* the long-horizon forecast converges to the unconditional variance (current code: converges to `omega/(1-sum(beta))`).

### P0-9 · Auth disabled by default + dead SecurityProperties — **W-C3** (M)
- **Fix:** (a) Make `SecurityConfig` constructor-inject `SecurityProperties` (add `@EnableConfigurationProperties`); remove the `@Value` field. (b) Change `application.yml` default to `AUTH_DISABLED:false`. (c) Add a startup `ApplicationRunner` that **fails the context** if `auth-disabled=true` and the active profile is not `local`/`dev`/`test`.
- **Test:** *given* profile `prod` with `auth-disabled=true`, *then* context startup throws (G-W-T). *given* `local`, *then* it starts.

### P0-10 · No global exception handler — **W-C1** (S)
- **Fix:** Add `@RestControllerAdvice` mapping `Exception`→`ProblemDetail` (RFC 7807) with sanitized message; log full stack server-side. Specific handlers for `IllegalArgumentException`, `MethodArgumentNotValidException`, `DataIntegrityViolationException`.
- **Test:** *given* a controller throws `IllegalArgumentException`, *then* the response is a 400 `ProblemDetail` with no stack trace.

### P0-11 · Missing `/api/v1/kpi/*` controller — **F-E1** (L)
- **Fix decision required (see §7):** either (a) implement `KpiController` backed by `computation` services, or (b) mark the dashboard out-of-contract (return 501 + stop the frontend 404-loop by gating queries). The verification showed ~8 endpoints missing, not 1.
- **Test:** contract test asserting each advertised KPI route returns either 200 with a typed body or an explicit 501 (never 404).

### P0-12 · Landing Dockerfile runs as root — **(orig-2)** (S)
- **Fix:** `USER nginx` in `landing/Dockerfile`; bind nginx to 8080 internally, map 80 externally.
- **Test:** build the image and assert `id -u` inside the container ≠ 0.

### P0-13 · `Eq553SlippageModel` returns `Double.MAX_VALUE` — **K-H2** (S) *(promoted to P0)*
- **Fix:** return a finite cap (e.g. 10_000 bps = 100%) or throw a documented `InsufficientLiquidityException`. `MAX_VALUE` cascades to `-Infinity` adjusted returns.
- **Test:** *given* zero dollar-volume, *then* slippage is finite (no `Infinity`/`NaN` downstream).

---

## 4. P1 — Data integrity, money precision, observability (do next)

### Cross-cutting workstream A: Money as `BigDecimal` / `NUMERIC` — **P-C1, W-C4, K-M1** (L)
- **Scope:** `persistence` entities + migrations, `web` DTOs/params, `computation` portfolio algebra. Leave `double` acceptable only for computed statistics (Sharpe, win rate, z-scores).
- **Migration plan:** additive — new `NUMERIC(20,8)`/`NUMERIC(16,4)` columns alongside existing `DOUBLE PRECISION`; backfill via `UPDATE ... SET new = old`; add a `V36+` migration to rename/drop after validation. Never rewrite `V1`–`V35`.
- **Jackson:** register `BigDecimal` as plain JSON number (not `{"scale", "value"}`); `@RequestParam` price → `String` parsed to `BigDecimal` with validation.
- **ADR:** see §6.
- **Tests:** G-W-T per repository round-trip asserting exact decimal equality (e.g. `0.1 + 0.2 == 0.3`).

### Cross-cutting workstream B: Transaction management — **P-C2** (M)
- **Fix:** `@Transactional(readOnly = true)` at class level on every repository; `@Transactional` on writes. For cross-repository atomicity (`VirtualPortfolioPosition.close()` + `VirtualPortfolioTradeRepository.save()`), introduce a `@Service` layer method annotated `@Transactional`.
- **Tests:** integration test (Testcontainers TimescaleDB) asserting a write that throws mid-batch rolls back the whole transaction.

### Cross-cutting workstream C: Observability plumbing — **I-H3, I-H4, I-M1** (L)
- **I-H3:** add `micrometer-registry-prometheus` to `app/build.gradle`. Until this lands, dashboards cannot work at all.
- **I-H4:** instrument the metric names the dashboards already reference — at minimum `ingestion_ticks_total`, `signals_generated_total`, `regime_state`, `kpi_computation_duration_seconds`, `ili_value`. Use a single `MetricsModule` registering `Counter`/`Gauge`/`Timer` beans.
- **I-M1:** either deploy Alertmanager with ≥1 receiver (Slack/email) or configure Grafana-managed alerting; remove the empty `targets: []`.
- **Tests:** `WebMvcTest` asserting `/actuator/prometheus` exposes each registered metric name.

### Remaining P1 (independent items)

| ID | Fix | Effort |
|---|---|---|
| **P-C3** compression | New migration adding `add_compression_policy` to the 25 uncompressed hypertables (segmentby/interval per TimescaleDB guidance) | M |
| **P-C4** retention | New migration adding `add_retention_policy` per table; make the tick_data 90-day window configurable | M |
| **P-H2** Flyway no-op | `TimescaleDbConfig`: set `baselineOnMigrate(true)` on the injected bean (`.configure().baselineOnMigrate(true).load()` is wrong) | S |
| **P-H4** strike in PK | Migration to `ALTER TABLE option_chain_snapshots … strike NUMERIC(16,4)`; coordinate with workstream A | S |
| **P-H5** `getKey()` NPE | Null-check in the 5 repositories; throw a descriptive `DataAccessException` | S |
| **P-H6** V28 idempotency | New migration replacing the view with `CREATE OR REPLACE VIEW` | S |
| **I-H1** AlgorithmicSanityGuard threading | `breaches` → `CopyOnWriteArrayList` | S |
| **I-H2** FileOverflowBuffer swallow | Propagate or expose a failure counter/health indicator | S |
| **I-H3 (ingestion)** HttpClient timeouts | `.connectTimeout(Duration.ofSeconds(10))` | S |
| **I-H1 (infra)** Grafana password | Remove `:-admin` default in prod compose; fail-fast on empty `GRAFANA_PASSWORD` | S |
| **I-H2 (infra)** monitoring volumes | Bind-mount prometheus/grafana/loki data to persistent EBS path | S |
| **C-H1..H4** CDM validation + lint | TTM divisor 365.0 (or new ACT_365_25 enum); log inverted bid/ask; add `>=0` checks; `checkstyle.ignoreFailures=false` | S |

### P1 analytics items (deferred-math-correctness, but high-value)
| ID | Fix | Effort |
|---|---|---|
| **A-H1** EVT Gumbel fallback | `if abs(xi) < 1e-6: VaR = u + beta*ln((n/nu)*(1-p))` | S |
| **A-H2** QuantReg intercept | prepend `np.ones(n)` to match OLS | S |
| **A-H4** GARCH init | initialize all slots to `omega/(1-sum(alpha)-sum(beta))` | S |

---

## 5. P2 — Robustness, validation, API contract typing

| ID | Fix | Effort |
|---|---|---|
| **P-H1** NULL→0.0 mappers | standardize on `rs.getObject("col") != null ? rs.getDouble("col") : null` in `CorrelationOutputRepository`, `ZscoreSeriesRepository` | S |
| **P-H3** LIKE leading wildcard | `strategy_config->>'name' ILIKE :namePattern` with GIN index | S |
| **P-H7** pagination | `LIMIT/:offset` + `PaginatedResponse<T>` wrapper on the 11 unbounded queries | M |
| **W-C2** `Map<String,Object>` → typed DTOs | define response records per endpoint (coordinate with workstream A for `BigDecimal` fields) | L |
| **W-C5** split DemoController | `DemoPortfolioController` / `DemoRiskController` / `DemoStrategyController`; drop unused `PaperTradingEngine`/`SignalLogRepository` injections | M |
| **W-H1** stub endpoints | implement or return 501 (never 200 + empty) | M |
| **W-H2/H3/H4/H5/H6/H7** security hardening | method-level RBAC on writes; `denyAll` catch-all; add `nosniff`/`Referrer-Policy`; rate-limit kill-switch/close-position (Bucket4j); configurable WS origins; WS ping/pong heartbeat | L |
| **F-H1** SignalToast timer | per-signal dismissal timestamps, not array-identity-driven effect | S |
| **F-H2** section-gated fetching | pass `activeSection` to `useDashboardData`; set `enabled` on queries | M |
| **F-H3** WS validation | Zod schemas for inbound WS frames; remove `as unknown as` private-field cast (expose `send()` on the manager) | M |
| **K-H1** PhantomLiquidity guard | sentinel/NaN + log when `totalVolumeAtBest` below threshold | S |
| **K-H3** Aumf magic 15 | named constant + Javadoc; verify against the intended volatility-zscore scale | S |
| **I-M2** dashboard image name | dedicated `dashboard_repository_url` variable | S |
| **I-M3** TimescaleDB tag | pin `timescale/timescaledb:2.16.1-pg16` | S |
| **I-M4** backup DLQ | SQS DLQ on the EventBridge target | S |
| **A-H3/A-H5** drift/entropy | document ABM-vs-GBM intent; add Miller-Madow bias correction or KSG estimator (or mark as approximate) | M |

---

## 6. ADR — architectural decisions (single record)

Per `CLAUDE.md`, create **ADR-021: Post-review remediation architecture** in `docs/adr/` capturing the four architectural decisions this plan forces:

1. **Monetary precision contract.** `BigDecimal` (Java) + `NUMERIC(p,s)` (SQL) for prices/PnL/strike/rates; `double` only for statistics. Includes the additive-migration strategy and the Jackson serialization choice.
2. **Transaction boundaries.** Repository `readOnly=true` default, `@Transactional` on writes, `@Service` layer for cross-repository atomicity. No JPA.
3. **Observability contract.** Metric taxonomy (names, labels, types) the dashboards depend on; ownership of instrumentation per module; Alertmanager vs. Grafana-managed alerting decision.
4. **Frontend↔backend contract governance.** How the OpenAPI types become the single source of truth; the rule that a route in the contract must have a controller or return 501 (never 404/empty-200).

Each decision records context, alternatives considered, and the verification evidence from `00-verification-report.md`.

---

## 7. Open decisions requiring the maintainer

These cannot be resolved by reading code; they are product/spec calls. Recommend resolving before the corresponding P0/P1 item starts.

1. **KPI controller (P0-11):** implement the 8 missing KPI endpoints now, or formally declare the dashboard out-of-contract for this release? *Recommendation:* implement `ili`, `ili/history`, `liquidity-stress`, `volatility-regime` (highest dashboard value); 501 the rest with a tracked ticket.
2. **Drift process (A-H3):** is ABM intentional (e.g. for a normalized index) or should it be GBM? The name "Ito process" + price-like defaults suggest GBM was intended.
3. **Money workstream scope (workstream A):** full BigDecimal migration (large, touches 35 entities) vs. critical-path-only (trade/portfolio/strike) with a documented remainder? *Recommendation:* critical-path-only for this pass; full migration tracked as follow-up.
4. **CNN-LSTM regime model (A-M2):** train it, remove it, or gate it behind a "research" flag? Returning random predictions silently is not acceptable in any option.

---

## 8. Sequencing & dependencies

```
P0 (parallelize across modules)
 ├─ K-C2 (IndicatorComputer)  ──┐
 ├─ K-C1, K-C4, K-H2           ├─ no inter-dependencies
 ├─ I-C1..C4 (4 PRs)            │
 ├─ C-C1, C-C2, C-C3            │
 ├─ A-C1                        │
 ├─ W-C3, W-C1                  │
 ├─ F-E1 (decision §7.1) ◄──────┼── needs §7.1 decision
 └─ landing USER, etc.         ──┘

P1
 ├─ Workstream A (BigDecimal) ◄── needs §7.3 decision; coordinates P-C1/W-C4/K-M1 + P-H4
 ├─ Workstream B (Transactions)  – P-C2
 ├─ Workstream C (Observability) – I-H3 before I-H4/I-M1
 └─ remaining P1 items (independent)

P2 – after contract decisions (W-C2/F-H3 depend on the DTO work in workstream A)
P3 – hygiene; lowest risk
```

A shared `StatisticsUtils` (K-H4/K-C4) is extracted once during P1 and adopted by the duplicated sites; it fixes the population-vs-sample variance inconsistency in one place.

---

## 9. Definition of Done (per finding)

A finding is closed when **all** hold:
1. Code fixed per this plan.
2. At least one Given-When-Then test covers the failure mode (fails on old code, passes on new).
3. No new lint warnings; `checkstyle.ignoreFailures` re-enabled (P3) once the existing backlog is clean.
4. The ADR (§6) updated if the change is architectural.
5. The finding's row in `00-verification-report.md` §6 marked resolved (or escalated/deferred with rationale).
