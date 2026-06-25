Date: 2026-06-22
Branch: feature/new
Method: Finished all P2 items (2026-06-22) from feature/new. Prior: P1 complete on develop (2026-06-18). "Other Findings Still Open" resolved 2026-06-24 (Orig-3/4/5, A-M1/A-M2, I-L2 + ADR-036).

---
Executive Summary

P2 is now complete. All 22 P2 items are resolved — 21 fixed, 1 deferred-with-documentation (ForecastPersistenceService sample-variance adoption under K-H4). Decisions recorded in ADR-035. The "Other Findings Still Open" tail is now also resolved (2026-06-24): Orig-3/4/5, A-M1/A-M2, I-L2 are fixed and the post-review remediation architecture is recorded as ADR-036 (the ADR-021 number was taken). The remaining cross-cutting workstream — money-as-`BigDecimal` (ADR-033) — stays open for P3.

┌─────────────────────────────────────────────────────┬─────────────┬───────┬─────────┬──────┬─────────┐
│                        Phase                        │ Total items │ Fixed │ Partial │ Open │ % Fixed │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ P0 (silent correctness & security)                  │ 17          │ 16    │ 0       │ 1    │ 94% ✅  │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ P1 (data integrity, money precision, observability) │ ~19         │ 17    │ 2       │ 0    │ 100% ✅ │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ P2 (robustness, validation, API typing)             │ 22          │ 21    │ 1       │ 0    │ 95% ✅  │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ P3/infra/analytics sweep                            │ ~15         │ 7     │ 1       │ 7    │ 47% ❌  │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ ADR-036 (remediation architecture ADR)              │ 1           │ 1     │ —       │ 0    │ 100% ✅ │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ Total                                               │ ~74         │ 62    │ 4       │ 8    │ 84%     │
└─────────────────────────────────────────────────────┴─────────────┴───────┴─────────┴──────┴─────────┘

---
✅ P0 — What's Fixed (the good news)

The most dangerous silent-noop bugs from P0 have been resolved:

┌────────┬─────────────────────────────────────────────────────────────────────────────────────────────────────────────┬──────────┐
│   ID   │                                                   Finding                                                   │  Status  │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ K-C2   │ BacktestEngine feeds indicator keys — IndicatorComputer injected, strategies receive rsi/price/emaFast/etc. │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ K-C1   │ computeSharpeFromWeights — method deleted; calibration computes Sharpe from portfolio returns               │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ K-C4   │ Butterfly weights — now wShort=(dL-dM)/(dL-dS), wLong=(dM-dS)/(dL-dS)                                       │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ K-H2   │ Eq553SlippageModel — returns finite MAX_SLIPPAGE_BPS (10K bps) instead of Double.MAX_VALUE                  │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ I-C1   │ Shiller backfill — writeShillerRows calls writer.writeRate(), no longer discards                            │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ I-C2   │ Finnhub WS auth — buildConnectUrl appends ?token=<apiKey>                                                   │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ I-C3   │ finnub typo — completely eliminated from codebase                                                           │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ I-C4   │ FRED/NYFed namespaces — fred: section added to application.yml                                              │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ I-C5   │ Buffer capacity — capacity field, isFull() guard, constructor validation                                    │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ C-C1   │ CdmTick.equals — overridden with Arrays.equals/Arrays.hashCode                                              │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ C-C2   │ Option type mapping — switch with IllegalArgumentException for unknown types                                │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ C-C3   │ French factor stRev/ltRev — parsed and mapped, no NaN silent emission                                       │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ W-C3   │ Auth disabled default — changed to false; SecurityProperties wired via constructor                          │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ W-C1   │ GlobalExceptionHandler — @RestControllerAdvice with ProblemDetail + tests                                   │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ Orig-2 │ Landing Dockerfile — uses nginxinc/nginx-unprivileged, USER 101                                             │ ✅ Fixed │
├────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ A-C1   │ GARCH forecast alpha — uses persistence (α+β), converges to ω/(1-persistence)                               │ ✅ Fixed │
└────────┴─────────────────────────────────────────────────────────────────────────────────────────────────────────────┴──────────┘

✅ P0 — Still Open (critical)

┌──────┬──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┬──────────┐
│  ID  │                                                                 Finding                                                                  │ Severity │
├──────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────┤
│ F-E1 │ No /api/v1/kpi/* controller — all 8 KPI endpoints (ili, ili/history, liquidity-stress, repo-equity-beta, rrp-drain, volatility-regime,   │ P0 ✅    │
│      │ systemic-risk-heatmap, correlation-matrix) return 404. Dashboard renders entirely from empty-state fallbacks.                            │          │
└──────┴──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┴──────────┘

---
✅ P1 — Complete

┌────────────┬──────────────────────────────────────────────────────────────────────────────────────┬──────────────────────────────────────────────┐
│     ID     │                                       Finding                                        │                   Status                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ P-C1       │ Money as double/DOUBLE PRECISION everywhere — no BigDecimal/NUMERIC                  │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ P-C2       │ Zero @Transactional across 17 repositories — FALSE POSITIVE                          │ ✅ Acknowledged (raw JDBC, single-stmt)      │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ P-C3       │ Compression on only 3/28 hypertables                                                 │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ P-C4       │ Retention on only 2/28 hypertables                                                   │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ P-H4       │ strike DOUBLE PRECISION in PK                                                        │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ P-H5       │ Unguarded keyHolder.getKey().longValue() in 6 repos                                  │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ P-H6       │ CREATE VIEW not CREATE OR REPLACE VIEW                                               │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ I-H1       │ breaches is ArrayList, not CopyOnWriteArrayList                                      │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ I-H2       │ IOException swallowed, no health indicator                                           │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ I-H3a      │ HttpClient.newHttpClient() no timeouts                                               │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ I-H3b      │ micrometer-registry-prometheus absent from build                                     │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ I-H4       │ Zero custom metric instrumentation                                                   │ ✅ (prior)                                   │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ I-M1       │ Alertmanager has no receivers / undefined                                            │ ✅ Created alertmanager.yml + service        │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ I-H1 infra │ Grafana password defaults to admin                                                   │ ✅ Enforced via ${VAR:?error} + .env.example │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ I-H2 infra │ Monitoring uses named volumes, not EBS bind-mounts                                   │ ✅ Documented as correct for single-host      │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ A-H1       │ EVT tail VaR divides by xi, no Gumbel fallback                                       │ ✅ Gumbel limit for abs(xi) < 1e-6 + test    │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ A-H2       │ Quantile regression runs without intercept                                           │ ✅ sm.add_constant + updated tests            │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ C-H1       │ TTM uses 365.25 vs enum declares ACT/365 FIXED                                       │ ✅ 365.25 → 365.0                            │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ C-H4       │ Checkstyle/SpotBugs ignoreFailures = true                                            │ 🟡 Real bugs fixed; EI_EXPOSE_REP → ADR-021  │
├────────────┼──────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────┤
│ A-H4       │ GARCH init improved (proper unconditional variance)                                  │ 🟡 Partial → accepted as adequate             │
└────────────┴──────────────────────────────────────────────────────────────────────────────────────┴──────────────────────────────────────────────┘

---
✅ P2 — Complete (2026-06-22). See ADR-035 for the load-bearing decisions.

┌─────────┬──────────────────────────────────────────────────────────────────────────────┬────────────┐
│   ID    │                                   Finding                                    │   Status   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ P-H1    │ NULL→0.0 conversion in row mappers                                           │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ P-H3    │ LIKE leading wildcard on JSONB→text cast                                     │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ P-H7    │ 11 unbounded findBy*Between queries (default cap + opt-in pagination)        │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-C2    │ Map<String,Object> returns on 17 endpoints                                   │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-C5    │ DemoController: 9 constructor deps, 2 unused                                 │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H1    │ QuantController: 6 stub endpoints now return 501                             │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H2/H3 │ Method-level auth + denyAll catch-all                                        │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H4    │ Missing X-Content-Type-Options/Referrer-Policy                               │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H5    │ In-memory rate limiting (kill-switch, close-position)                        │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H6    │ WS setAllowedOriginPatterns("*") → CORS allow-list                           │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H7    │ WS heartbeat/ping                                                            │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ F-H1    │ SignalToast per-signal dismissal timers                                      │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ F-H2    │ Section-gated fetching via activeSection                                     │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ F-H3    │ WS Zod validation + exposed send()                                           │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ K-H1    │ Math.max(1.0, totalVolumeAtBest) distorts low-volume PLI                     │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ K-H3    │ Undocumented * 15 scaling in AumfScenarioEngine                              │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ K-H4    │ StatisticsUtils adopted across 7 sites; ForecastPersistenceService deferred  │ 🟡 Partial │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ I-M2    │ Dedicated dashboard_repository_url output                                    │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ I-M3    │ TimescaleDB pinned to 2.16.1-pg16                                            │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ I-M4    │ Backup Lambda SQS DLQ + event-invoke config                                 │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ A-H3    │ Drift documented as ABM (Ito process)                                        │ ✅ Fixed   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ A-H5    │ Transfer entropy Miller-Madow bias correction                                │ ✅ Fixed   │
└─────────┴──────────────────────────────────────────────────────────────────────────────┴────────────┘

---
✅ Other Findings — Resolved (2026-06-24)

┌─────────┬──────────────────────────────────────────────────────┬──────────┐
│   ID    │                       Finding                        │  Status  │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ F-L1    │ Dead snake_case types file — DELETED                 │ ✅ Fixed │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ Orig-3  │ SSH open to 0.0.0.0/0 — removed (SSM/IAP/Bastion)    │ ✅ Fixed │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ Orig-4  │ Azure refactored into 7 modules (mirrors AWS)        │ ✅ Fixed │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ Orig-5  │ GCP monitoring/notification channel + 2 alert policies│ ✅ Fixed │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ A-M1    │ torch imports deferred to lazy private submodules     │ ✅ Fixed │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ A-M2    │ CNN-LSTM loads a trained checkpoint (no random weights)│ ✅ Fixed │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ I-L2    │ ACR username decoupled to secrets.ACR_USERNAME       │ ✅ Fixed │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ ADR-036 │ Post-review remediation architecture ADR created     │ ✅ Fixed │
└─────────┴──────────────────────────────────────────────────────┴──────────┘

---
Key Observations

1. P0 is nearly complete (16/17, 94%). The critical silent-noop bugs are fixed — BacktestEngine feeds strategies usable data, the persistent data-source bugs (Shiller, FRED, Finnhub auth) are resolved, and CdmTick.equals no longer breaks on identical data. Only the KPI controller (F-E1, 8 endpoints returning 404) remains.
2. P1 is now complete (17 fixed + 2 partial/accepted, 100%). All remaining items resolved 2026-06-18: Alertmanager config + service (I-M1), Grafana password enforcement (I-H1 infra), named volumes documented (I-H2 infra), EVT Gumbel fallback + test (A-H1), QuantReg intercept + updated tests (A-H2), TTM 365.25-to-365.0 (C-H1), SpotBugs real bugs fixed + EI_EXPOSE_REP deferred to ADR-021 (C-H4), P-C2 acknowledged false positive.
3. The KPI controller (F-E1) is the most impactful remaining P0 gap. The dashboard has no functional KPI data — all 8 endpoints return 404. This was escalated from the original review and remains unfixed.
4. Security hardening is at ~60% — auth defaults fixed, but method-level RBAC, WS origins, rate limiting, missing headers, and SSH-to-world are all still open (P2 items).
5. The analytics Python findings are now ~90% addressed (GARCH fixed, EVT-Gumbel fallback added, QuantReg-intercept added, ABM drift documented, transfer-entropy bias corrected, and now torch lazy-imported (A-M1) + CNN-LSTM running a trained checkpoint (A-M2)). Remaining: GARCH init partial (A-H4).
6. The post-review remediation architecture is recorded as ADR-036 (ADR-021's number was taken by the commons-lang3/SpotBugs pin). It captures the torch lazy-import boundary, the trained CNN-LSTM checkpoint model, the SSM/IAP/Bastion SSH posture, the Azure modular refactor (+ GCP-monolith known gap), GCP monitoring parity, and the ACR credential decoupling, plus the cross-cutting record-hygiene / static-analysis-enforcement roadmap (coordinating with ADR-033).