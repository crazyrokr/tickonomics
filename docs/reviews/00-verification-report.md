# Review Verification Report

**Date:** 2026-06-14
**Scope:** Independent verification of all 8 review documents in `docs/reviews/`
**Method:** One verification pass per module; every Critical/High finding re-checked against the current source tree (not the review's quoted text). Grep evidence gathered where claims were quantitative.
**Branch:** `feature/production-infrastructure`

---

## 1. Purpose

The eight review documents report ~250 findings. Before committing remediation effort, this pass answers three questions per finding:

1. **Is it real?** (CONFIRMED vs FALSE-POSITIVE)
2. **Is it still present?** (ALREADY-FIXED since the review was written)
3. **Is the severity accurate?** (PARTIALLY / escalation / downgrade)

Verification focused on Critical and High findings (the drivers of the elimination plan). Medium/Low findings are accepted from the reviews except where a verification agent incidentally disproved one.

---

## 2. Scoreboard

| Module | Crit+High verified | CONFIRMED | FALSE-POSITIVE | PARTIALLY | ALREADY-FIXED | Net change |
|---|---|---|---|---|---|---|
| 01 Analytics (Python) | 8 | 8 | 0 | 0 | 0 | — |
| 02 CDM (Java) | 7 | 6 | 0 | 1 | 0 | C-C1 mechanism corrected |
| 03 Ingestion (Java) | 9 | 8 | 0 | 1 | 0 | I-#7 storm downgraded |
| 04 Persistence (SQL/Java) | 11 | 11 | 0 | 0 | 0 | — |
| 05 Computation (Java) | 12 | 8 | 1 | 3 | 0 | 1 FP, 1 severe escalation |
| 06 Web/App (Java) | 16 | 14 | 2 | 0 | 0 | 2 FP; auth default escalated |
| 07 Infrastructure | 17 | 11 | 0 | 2 | 2 (+2 PARTIAL SSH) | 2 ALREADY-FIXED; DC3 escalated |
| 08 Frontend (TS) | 5 | 4 | 0 | 1 | 0 | snake/camel downgraded; KPI gap escalated |
| **Total** | **85** | **70** | **3** | **8** | **2** | |

**Bottom line:** The reviews are largely accurate (70/85 confirmed). The 3 false positives and 8 partials are listed below in detail — they would have wasted remediation effort if accepted uncritically. The escalations are the more important finding: several issues are **worse** than the reviews state.

---

## 3. False Positives (do NOT fix)

These findings are inaccurate and require no action.

### 3.1 `RiskPremiumResidualMonitor` look-ahead bias — FALSE POSITIVE
**Review claim (05-§2.7, Critical):** `residualStd` is computed from historical residuals that include the current observation, creating look-ahead bias.
**Verification:** `RiskPremiumResidualMonitor.java:40-42` computes `residualStd = computeStd(historicalResiduals)` over the **passed-in** historical list only. The current residual is never appended before `computeStd`. No look-ahead. The method is clean; the caller owns the exclusion of the current observation.
**Action:** Drop from plan.

### 3.2 Zero test coverage for WebSocket handlers — FALSE POSITIVE
**Review claim (06-§2.11, High):** No tests for `BroadcastWebSocketHandler` / `PriceWebSocketHandler` / `SignalWebSocketHandler`.
**Verification:** `web/src/test/` contains `realtime/PriceWebSocketHandlerSpec.groovy` (3 tests), `realtime/SignalWebSocketHandlerSpec.groovy` (2 tests), plus controller and config specs. The abstract `BroadcastWebSocketHandler` logic is exercised via its concrete subclasses' Spock specs.
**Action:** Drop from plan. Only `RealtimeWebSocketConfig` registration itself lacks a dedicated test (Low).

### 3.3 Integer division in `overflowUtilization()` — FALSE POSITIVE
**Review claim (06-§3.14, Medium):** `100.0 * buffer.size() / bufferCapacity` performs integer division.
**Verification:** `CrossModuleResilienceHealthProbe.java:62` — the leading `100.0` is a double literal; `*` and `/` are left-associative at equal precedence, so evaluation is `(100.0 * size) / cap`, which promotes `size` to double **before** the division. No truncation occurs.
**Action:** Drop from plan.

---

## 4. Partials / Downgrades (fix, but cheaper than the review implies)

### 4.1 `CdmTick.equals()` — CONFIRMED, mechanism corrected
**Review claim:** Record auto-generates broken `equals` on `int[]` *and* there is no defensive copying.
**Verification:** The equals bug is **real** (records use component `equals`, and `int[].equals` is reference equality, so two ticks with identical condition contents compare unequal). But defensive copying **is** present (`CdmTick.java:18` compact ctor clone + `:22-24` accessor clone) — and ironically it *guarantees* the arrays are never the same reference, so the bug is always observable.
**Action:** Keep as Critical. Fix by overriding `equals`/`hashCode` with `Arrays.equals`/`Arrays.hashCode` (or switching the component to `List<Integer>`).

### 4.2 Finnhub reconnect "storm" — PARTIALLY (downgrade High → Medium)
**Verification:** `FinnhubWsClient.scheduleReconnect` has a real non-atomic read-modify-write on `reconnectDelayMs` (volatile only) and no re-entrancy guard, so double-scheduling can occur. But the **single-threaded** `reconnectScheduler` serializes actual `doConnect` execution, so the "storm of duplicate connections" severity is overstated. The atomicity/guard defect is real but the blast radius is smaller.
**Action:** Fix the atomicity (use `AtomicInteger` + a CAS guard), but Medium priority.

### 4.3 `PortfolioManagementAlgebra.totalCost` — CONFIRMED but cosmetic (Critical → Medium)
**Verification:** `totalCost = (t0Rate + t1Rate) * notional + spreadCost` is **numerically identical** to `t0Cost + t1Cost + spreadCost` given the current code. `t0Cost`/`t1Cost` are computed and used only in the debug log. This is a maintainability hazard (future divergence), not a present-day correctness bug.
**Action:** Medium. Replace with `t0Cost + t1Cost + spreadCost` for consistency.

### 4.4 Population-variance Sharpe — PARTIALLY (Critical → Medium)
**Verification:** 4 of 5 Sharpe/standard-deviation sites use ÷N (`DelayDExecutor:118`, `FireflyWeightOptimizer:150`, `ScheduledCalibrationTask:223`, `RiskPremiumResidualMonitor` via `.average()`). `ForecastPersistenceService:128` correctly uses ÷(N−1). The bias is real but small for typical lookback windows; the right fix is a shared `StatisticsUtils` so the decision lives in one place. This is part of the duplicated-statistics workstream, not a standalone Critical.
**Action:** Medium. Fold into the `StatisticsUtils` extraction.

### 4.5 `AnomalyScoringService` unchecked casts — PARTIALLY (High → Medium)
**Verification:** The casts exist (`:174-175`) but are followed by null/size validation (`:177-180`) and guarded by `@SuppressWarnings`. The risk is lower than stated; a malformed Python worker response would throw `ClassCastException`, not silently corrupt data.
**Action:** Medium. Add `instanceof` guards; lower priority than the data-loss bugs.

### 4.6 `BarrierHittingTimeAnalyzer` exponent — PARTIALLY (High → Low/Medium)
**Verification:** The `0.5*sigma²` term in the drift is the standard Itô/convexity correction for the first-passage probability of a log-normal process. The formula is **not wrong**; it is merely undocumented. The genuine concern is the unrelated `Math.max(1, monitoringPoints)` in `expectedHittingTime`.
**Action:** Low/Medium. Add a Javadoc citation; verify `monitoringPoints` guard.

### 4.7 Frontend snake_case vs camelCase — PARTIALLY (High → Low)
**Review claim (08):** OpenAPI types use snake_case; dashboard types use camelCase; no transformation layer → all property accesses return `undefined` at runtime.
**Verification:** The dead auto-generated `lib/api/types.ts` does use snake_case and disagrees with reality. **But the runtime client (`lib/api.ts`) imports exclusively from `@/types/api` (camelCase), and the Java backend emits camelCase** (no `PropertyNamingStrategy.SNAKE_CASE`, no `@JsonProperty`, no `spring.jackson` config; record components are camelCase). There is **no runtime bug**. The only real issue is two divergent type sources (one stale/dead).
**Action:** Low. Delete or regenerate `lib/api/types.ts` so there is a single source of truth.

---

## 5. Escalations (worse than the reviews state)

These are the most important outputs of verification.

### 5.1 The entire `/api/v1/kpi/*` surface is missing — not just `correlation-matrix`
**Review claim (08):** One missing endpoint: `/api/v1/kpi/correlation-matrix`.
**Verification:** The backend has only three controllers — `HealthController`, `QuantController`, `DemoController`. **There is no KPI controller at all.** `grep` for `/api/v1/kpi` controller mappings is empty. So every KPI call the dashboard makes — `ili`, `ili/history`, `liquidity-stress`, `repo-equity-beta`, `rrp-drain`, `volatility-regime`, `systemic-risk-heatmap`, and `correlation-matrix` — returns 404. The OpenAPI contract lists these routes; no Spring controller implements them. The dashboard renders entirely from empty-state fallbacks.
**Impact:** The frontend↔backend integration is broken at the contract level, not just one endpoint.
**Action:** Promote to P0. Either implement the KPI controller or formally mark the dashboard as out-of-contract and stop the frontend from 404-looping.

### 5.2 No Micrometer Prometheus registry on the classpath — dashboards cannot work at all
**Review claim (07-DC3):** Grafana dashboards reference non-existent metrics.
**Verification:** `app/build.gradle` depends on `spring-boot-starter-actuator` but **not** `micrometer-registry-prometheus`. Without that dependency, `/actuator/prometheus` exposes only a fraction of JVM defaults (and the endpoint may 404). Combined with zero custom metric instrumentation in `computation/`/`ingestion/`/`web/`, every domain dashboard panel is permanently empty — not "empty until instrumented," but "empty because the plumbing does not exist."
**Action:** P1. Add the registry dependency; then the metric-instrumentation work becomes worthwhile.

### 5.3 Auth is disabled by **default true** in `application.yml`
**Review claim (06-§1.3):** `@Value` default is `false`, but demo profile likely sets true.
**Verification:** `app/src/main/resources/application.yml:36` declares `security.auth-disabled: ${AUTH_DISABLED:true}` — the **shipped default is `true`** (auth OFF). `SecurityConfig` reads `@Value("${security.auth-disabled:false}")` (default false), but the property file overrides to true. `SecurityProperties` (the `@ConfigurationProperties` record) is dead — never injected, no `@EnableConfigurationProperties`. So out-of-the-box, all `/api/**` is authenticated-only **per the chain** but the WebSocket/other surfaces are `permitAll`, and auth is off in the default profile.
**Action:** P0. Wire `SecurityProperties`; make auth-disabled default to `false`; fail-fast on `auth-disabled=true` in non-local profiles.

### 5.4 `BacktestEngine` never feeds strategies usable input keys
**Review claim (05-§2.8):** Strategies expect keys like `"rsi"`, `"price"`, `"upperBand"` but receive `"return_0".."return_n"`.
**Verification:** CONFIRMED and severe. `grep` found only the single `input.put("return_"+i, …)` site across the entire backtest path; no code ever populates the indicator keys strategies read. **Every one of the 25 equity strategies returns `AlphaSignal.neutral()` on every iteration.** Backtests produce no positions, no PnL — silently. This invalidates any backtest-derived confidence in the strategies.
**Action:** P0. This is the single most damaging bug in the codebase — it makes the entire backtest subsystem a no-op while appearing to work.

---

## 6. Confirmed Findings Inventory (actionable)

Confirmed Critical/High findings below form the input to `00-elimination-plan.md`. IDs are `{module}-C{n}` / `{module}-H{n}`.

### 6.1 Analytics (Python) — all CONFIRMED
| ID | Sev | Finding | Location |
|---|---|---|---|
| A-C1 | 🔴 | GARCH multi-step forecast omits alpha term (`persistence` computed but unused) | `risk_service.py:75` |
| A-H1 | 🟠 | EVT tail VaR divides by xi, no Gumbel fallback | `evt_risk_service.py:32` |
| A-H2 | 🟠 | Quantile regression runs without intercept (inconsistent with OLS) | `quantile_regression_service.py:19` |
| A-H3 | 🟠 | Drift/Ito process is ABM, not GBM; undocumented | `drift_service.py:24` |
| A-H4 | 🟠 | GARCH conditional-variance init inconsistent (sample var vs ω) | `risk_service.py:147-148` |
| A-H5 | 🟠 | Transfer entropy uses biased naive histogram estimator | `transfer_entropy_service.py:10` |
| A-M1 | 🟡 | (bonus) `regime_service.py`/`anomaly_service.py` import torch at module level | — |
| A-M2 | 🟡 | (bonus) CNN-LSTM regime model used with random/untrained weights | `regime_service.py:61-67` |

### 6.2 CDM (Java)
| ID | Sev | Finding | Location |
|---|---|---|---|
| C-C1 | 🔴 | `CdmTick.equals` reference equality on `int[]` (real bug; defensive copy present) | `CdmTick.java` |
| C-C2 | 🔴 | Non-`"CALL"` option type silently mapped to PUT | `YahooOptionsCdmAdapter.java:20` |
| C-C3 | 🔴 | `FrenchFactorCdmAdapter` always sets stRev/ltRev to NaN; `FrenchFactorRow` lacks those fields | `FrenchFactorCdmAdapter.java:18` |
| C-H1 | 🟠 | TTM uses 365.25 but enum declares ACT/365 FIXED | `YahooOptionsCdmAdapter.java:51` |
| C-H2 | 🟠 | Inverted bid/ask silently corrected, no log | `YahooOptionsCdmAdapter.java:25-27` |
| C-H3 | 🟠 | `CdmBondSnapshot` no duration/convexity ≥ 0 validation; rateDelta/rateGamma unvalidated | `CdmBondSnapshot.java:27-38` |
| C-H4 | 🟠 | Checkstyle `ignoreFailures = true` (and SpotBugs) — lint never fails build | `build.gradle:57-64` |

### 6.3 Ingestion (Java)
| ID | Sev | Finding | Location |
|---|---|---|---|
| I-C1 | 🔴 | Shiller backfill parses & discards rows; never writes | `DataHubBackfillClient.java:74-76` |
| I-C2 | 🔴 | Finnhub WS connects without `?token=` | `FinnhubWsClient.java:83` |
| I-C3 | 🔴 | `@Value("${monitor.finnub.api-key:}")` typo (missing 'h'); yml has `finnhub` | `FinnhubEquityClient.java:32`, `FinnhubNewsClient.java:32` |
| I-C4 | 🔴 | `fred.*`/`nyfed.*` property namespaces absent from yml; FRED key resolves empty | `FredClient.java:36-40`, `NyFedClient.java:34` |
| I-C5 | 🔴 | `InMemoryIngestionBuffer.add()` has no capacity enforcement | `InMemoryIngestionBuffer.java:20-22` |
| I-H1 | 🟠 | `AlgorithmicSanityGuard.breaches` is unsynchronized `ArrayList` | `AlgorithmicSanityGuard.java:30` |
| I-H2 | 🟠 | `FileOverflowBuffer` swallows IOException → silent data loss | `FileOverflowBuffer.java:29-41` |
| I-H3 | 🟠 | `HttpClientConfig` returns `HttpClient.newHttpClient()` with no timeouts | `HttpClientConfig.java:19-21` |
| I-M1 | 🟡 | (downgraded) Finnhub reconnect non-atomic delay bookkeeping | `FinnhubWsClient.java:202-209` |

### 6.4 Persistence (SQL/Java) — all CONFIRMED with grep evidence
| ID | Sev | Finding | Evidence |
|---|---|---|---|
| P-C1 | 🔴 | All financial values `DOUBLE PRECISION`/`double`; no NUMERIC/BigDecimal | spot-checked TickData, V1, V21, VirtualPortfolioTrade |
| P-C2 | 🔴 | Zero `@Transactional` across all 17 repositories | `grep` count = 0 |
| P-C3 | 🔴 | Compression on only 3/28 hypertables | tick_data, factor_returns, index_snapshots |
| P-C4 | 🔴 | Retention on only 2/28 hypertables | tick_data (90d), volatility_forecasts (365d) |
| P-H1 | 🟠 | NULL→0.0 silent conversion in row mappers | `CorrelationOutputRepository.java:44-46`, `ZscoreSeriesRepository.java:48-50` |
| P-H2 | 🟠 | `FlywayMigrationStrategy` sets `baselineOnMigrate` on throwaway instance | `TimescaleDbConfig.java:19-27` |
| P-H3 | 🟠 | `strategy_config::text LIKE '%...%'` — leading wildcard, JSONB→text cast | `BacktestResultRepository.java:74-76` |
| P-H4 | 🟠 | Option `strike DOUBLE PRECISION` in PRIMARY KEY | `V21__create_option_chain_snapshots.sql:5,19` |
| P-H5 | 🟠 | Unguarded `keyHolder.getKey().longValue()` in 5 repositories | BacktestResult, VirtualPortfolioPosition, ModelArtifact, VirtualPortfolioTrade, SignalQualityReport |
| P-H6 | 🟠 | `V28` `CREATE VIEW` not `CREATE OR REPLACE VIEW` | `V28__create_evt_risk_metrics_view.sql:7` |
| P-H7 | 🟠 | 11 unbounded `findBy*Between` time-range queries, no LIMIT | TickData, SignalLog (×2), IliHistory, CorrelationOutput, ZscoreSeries, AlphaSignal (×2), RateSnapshot, VolatilityForecast, BacktestResult, SignalQualityReport |

### 6.5 Computation (Java)
| ID | Sev | Finding | Location |
|---|---|---|---|
| K-C1 | 🔴 | `computeSharpeFromWeights` computes stats of weights, not returns | `ScheduledCalibrationTask.java:196-207` |
| K-C2 | 🔴 | `BacktestEngine` feeds strategies `"return_N"` keys they never read → all signals neutral | `BacktestEngine.java:65-68` |
| K-C3 | 🔴 | Python-style `{:.Nf}` in SLF4J logs (18 sites) | WalkForwardValidator, RegimeDetector, ComparativeExecutionAnalysis, SentimentVolatilityGuard, AgnosticAggregator, PairsTradingEngine, MarketSensitivityLibrary, MarketEfficiencyMonitor, ScheduledCalibrationTask, SaliProcessor, AlgorithmicBehaviorAlignment, LeverageSignaler, PortfolioManagementAlgebra, RiskPremiumResidualMonitor |
| K-C4 | 🔴 | `FixedIncomePortfolioBuilder` butterfly weights violate duration-neutrality | `FixedIncomePortfolioBuilder.java:77-79` |
| K-H1 | 🟠 | `PhantomLiquidityService` div-zero guard `Math.max(1.0, totalVolumeAtBest)` distorts low-volume PLI | `PhantomLiquidityService.java:14` |
| K-H2 | 🟠 | `Eq553SlippageModel` returns `Double.MAX_VALUE` → −∞ adjusted return | `Eq553SlippageModel.java:11-13` |
| K-H3 | 🟠 | `AumfScenarioEngine` undocumented `* 15` scaling | `AumfScenarioEngine.java:72` |
| K-H4 | 🟠 | Duplicated statistics across 8+ files; no shared util | PairsTradingEngine, RiskPremiumResidualMonitor, ScheduledCalibrationTask, CrossModelValidator, WalkForwardValidator, ForecastPersistenceService, FireflyWeightOptimizer, UniverseAggregator |
| K-M1 | 🟡 | (downgraded) `totalCost` recomputed from raw rates — numerically identical, cosmetic | `PortfolioManagementAlgebra.java:24` |
| K-M2 | 🟡 | (downgraded) Population-variance Sharpe in 4 sites | see §4.4 |

### 6.6 Web / App (Java)
| ID | Sev | Finding | Evidence |
|---|---|---|---|
| W-C1 | 🔴 | No `@ControllerAdvice`/`@RestControllerAdvice` anywhere | `grep` count = 0 |
| W-C2 | 🔴 | `Map<String,Object>` returns: 13 DemoController + 4 QuantController endpoints | `DemoController.java`, `QuantController.java` |
| W-C3 | 🔴 | Auth disabled by default **true** in yml; `SecurityProperties` dead code | `application.yml:36`, `SecurityConfig.java:20`, `SecurityProperties.java` |
| W-C4 | 🔴 | `double` for price/monetary in DTOs and controller params | `PriceTick.java:12-13`, `DemoController.java:129,141,192` |
| W-C5 | 🔴 | `DemoController` 9 constructor deps; `PaperTradingEngine` + `SignalLogRepository` injected but unused | `DemoController.java:40-59` |
| W-H1 | 🟠 | 6 of 7 `QuantController` endpoints return hardcoded stubs with 200 OK | `QuantController.java:30,35,41,46,51,79` |
| W-H2 | 🟠 | No method-level authorization (`grep` hasRole/hasAuthority = 0) | `SecurityConfig.java` |
| W-H3 | 🟠 | Both security branches end `.anyRequest().permitAll()` | `SecurityConfig.java:33,52` |
| W-H4 | 🟠 | Missing `X-Content-Type-Options`/`Referrer-Policy` headers | `SecurityConfig.java:35-41,56-61` |
| W-H5 | 🟠 | No rate limiting | `grep` bucket4j/ratelimit = 0 |
| W-H6 | 🟠 | WebSocket `.setAllowedOriginPatterns("*")` | `RealtimeWebSocketConfig.java:27-28` |
| W-H7 | 🟠 | WebSocket has no heartbeat/ping | `BroadcastWebSocketHandler.java` |

### 6.7 Infrastructure
| ID | Sev | Finding | Status |
|---|---|---|---|
| (orig-1) | — | `forecast_trigger.py` was a no-op | ✅ ALREADY-FIXED (`ec2.run_instances`, ADR-019) |
| (orig-4) | — | Secrets baked into cloud-init | ✅ ALREADY-FIXED (SSM Parameter Store module) |
| I-H1 | 🟠 | Grafana admin password defaults to `"admin"` | `docker-compose.prod.yml:153` |
| I-H2 | 🟠 | Prometheus/Grafana/Loki use ephemeral named volumes, not EBS bind-mount | `docker-compose.prod.yml:127,149,174` |
| I-H3 | 🟠 | **No `micrometer-registry-prometheus` dependency** → `/actuator/prometheus` effectively non-functional | `app/build.gradle:9` |
| I-H4 | 🟠 | Grafana dashboards reference ~25 non-instrumented metrics; zero metric code in Java | `monitoring/grafana/dashboards/*.json` |
| I-M1 | 🟡 | `forecast_trigger.py` Alertmanager has no receivers (targets `[]`) | `monitoring/prometheus.yml:7-8` |
| I-M2 | 🟡 | Dashboard image name derived by string concat (`-dashboard`) | `environments/aws/main.tf:173` |
| I-M3 | 🟡 | TimescaleDB floating `latest-pg16` tag in base compose | `docker-compose.yml:56` |
| I-M4 | 🟡 | Backup Lambda EventBridge target has no DLQ | `modules/backup/main.tf:164-168` |
| I-M5 | 🟡 | Duplicate unused hosting security group in module | `modules/hosting/main.tf:121-152` |
| I-L1 | 🟢 | Prometheus endpoint exposed unauthenticated (auth off by default) | `application.yml:48-52` |
| I-L2 | 🟢 | ACR push uses `secrets.ACR_NAME` for both registry and username | `forecast-deploy.yml:120-122` |
| (orig-2) | 🟠 | Landing Dockerfile runs as root | still open |
| (orig-3) | 🟠 | SSH still open to `0.0.0.0/0` on **spot SG** and **GCP** (hosting SG fixed) | `networking/variables.tf:18-22`, `gcp/main.tf:23` |
| (orig-10/11) | 🟡 | ECR read wildcard (AWS-managed); `iam:PassRole` `*` (constrained to ec2 service) | `hosting/main.tf:96-99`, `orchestrator/iam.tf:36-47` |
| (orig-2) | 🟠 | Azure environment still monolithic (no modules) | `environments/azure/` |
| (orig-5) | 🟠 | GCP has no monitoring/alerting | `environments/gcp/main.tf` |

### 6.8 Frontend (TypeScript)
| ID | Sev | Finding | Location |
|---|---|---|---|
| F-E1 | 🔴 (escalated) | Entire `/api/v1/kpi/*` surface absent from backend (~8 endpoints, not 1) | `hooks/useKpiData.ts`; no KPI controller exists |
| F-H1 | 🟠 | `SignalToast` auto-dismiss timer reset on every signal → never dismisses under active stream | `SignalToast.tsx:32-44` |
| F-H2 | 🟠 | All dashboard sections fetch simultaneously; queries have no `enabled` gating by tab | `useDashboardData.ts:18-29`, `useKpiData.ts` |
| F-H3 | 🟠 | WebSocket unsafe casts; no runtime validation (Zod/type-guard) | `useWebSocket.ts:31-33`, `useSignals.ts:24`, `lib/websocket.ts:47-57` |
| F-L1 | 🟢 | (downgraded) Dead snake_case `lib/api/types.ts` diverges from real camelCase wire format | `lib/api/types.ts` |

---

## 7. Cross-cutting themes

1. **Silent no-ops that look like working features.** `BacktestEngine` (K-C2), Shiller backfill (I-C1), Finnhub REST/WS (I-C2/I-C3), FRED (I-C4), the missing KPI controller (F-E1), and the untrained CNN-LSTM (A-M2) all return plausible-but-empty output. These are the highest-risk class because monitoring will not flag them.
2. **Money as `double`.** Appears in persistence (P-C1), web (W-C4), and computation (K-M1). One workstream, not three.
3. **Transaction/atomicity gaps.** P-C2 (no `@Transactional`), I-C5 (unbounded buffer), I-H2 (swallowed IOException), I-M4 (no DLQ). All "silent data loss on partial failure" defects.
4. **Observability is plumbed for metrics that do not exist.** Dashboards (I-H4) + missing registry (I-H3) + dead Alertmanager (I-M1) means the monitoring stack is decorative until instrumentation lands.
5. **Security defaults are open.** Auth off by default (W-C3), `permitAll` catch-all (W-H3), WS wildcard origin (W-H6), Grafana `admin` (I-H1), SSH-to-world on spot/GCP (orig-3). One hardening pass.

---

## 8. Recommendation

Proceed to `00-elimination-plan.md`, which sequences the confirmed findings above into four phases (P0–P3) with concrete fixes, tests (Given-When-Then per project convention), and an ADR for the architectural decisions (BigDecimal, transaction management, observability instrumentation).
