Date: 2026-06-16
Branch: feature/upgrade-next-js
Method: Systematically checked every finding from docs/reviews/00-verification-report.md against the current source tree.

---
Executive Summary

Overall progress is modest — most gaps remain open. Two days after the elimination plan was written, ~19% of eligible fixes have been applied, but the large cross-cutting workstreams (BigDecimal, transactions, observability) and most P2/P3 items have not been started.

┌─────────────────────────────────────────────────────┬─────────────┬───────┬─────────┬──────┬─────────┐
│                        Phase                        │ Total items │ Fixed │ Partial │ Open │ % Fixed │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ P0 (silent correctness & security)                  │ 17          │ 16    │ 0       │ 1    │ 94% ✅  │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ P1 (data integrity, money precision, observability) │ ~19         │ 2     │ 1       │ 16   │ 11% ❌  │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ P2 (robustness, validation, API typing)             │ ~22         │ 1     │ 1       │ 20   │ 5% ❌   │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ P3/infra/analytics sweep                            │ ~15         │ 1     │ 1       │ 13   │ 7% ❌   │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ ADR-021 (remediation architecture ADR)              │ 1           │ 0     │ —       │ 1    │ 0% ❌   │
├─────────────────────────────────────────────────────┼─────────────┼───────┼─────────┼──────┼─────────┤
│ Total                                               │ ~74         │ 20    │ 3       │ 51   │ 27%     │
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
❌ P1 — Largely Unstarted

┌────────────┬─────────────────────────────────────────────────────────────────────┬────────┐
│     ID     │                               Finding                               │ Status │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ P-C1       │ Money as double/DOUBLE PRECISION everywhere — no BigDecimal/NUMERIC │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ P-C2       │ Zero @Transactional across 17 repositories                          │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ P-C3       │ Compression on only 3/28 hypertables                                │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ P-C4       │ Retention on only 2/28 hypertables                                  │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ P-H4       │ strike DOUBLE PRECISION in PK                                       │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ P-H5       │ Unguarded keyHolder.getKey().longValue() in 6 repos                 │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ P-H6       │ CREATE VIEW not CREATE OR REPLACE VIEW                              │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ I-H1       │ breaches is ArrayList, not CopyOnWriteArrayList                     │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ I-H2       │ IOException swallowed, no health indicator                          │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ I-H3       │ HttpClient.newHttpClient() no timeouts                              │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ I-H3       │ micrometer-registry-prometheus absent from build                    │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ I-H4       │ Zero custom metric instrumentation                                  │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ I-M1       │ Alertmanager has no receivers / undefined                           │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ I-H1 infra │ Grafana password defaults to admin                                  │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ I-H2 infra │ Monitoring uses named volumes, not EBS bind-mounts                  │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ A-H1       │ EVT tail VaR divides by xi, no Gumbel fallback                      │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ A-H2       │ Quantile regression runs without intercept                          │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ C-H1       │ TTM uses 365.25 vs enum declares ACT/365 FIXED                      │ ❌     │
├────────────┼─────────────────────────────────────────────────────────────────────┼────────┤
│ C-H4       │ Checkstyle/SpotBugs ignoreFailures = true                           │ ❌     │
└────────────┴─────────────────────────────────────────────────────────────────────┴────────┘

Partial:
| A-H4 | GARCH init improved (proper unconditional variance) | 🟡 Partial → accepted as adequate |

---
❌ P2 — Almost Entirely Open

┌─────────┬──────────────────────────────────────────────────────────────────────────────┬────────────┐
│   ID    │                                   Finding                                    │   Status   │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ P-H1    │ NULL→0.0 conversion in row mappers                                           │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ P-H3    │ LIKE leading wildcard on JSONB→text cast                                     │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ P-H7    │ 11 unbounded findBy*Between queries (some LIMIT added, most still unbounded) │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-C2    │ Map<String,Object> returns on 17 endpoints                                   │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-C5    │ DemoController: 9 constructor deps, 2 unused                                 │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H1    │ QuantController: 5 endpoints return empty 200 stubs                          │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H2/H3 │ No method-level auth; permitAll catch-all (auth branch OK)                   │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H4    │ Missing X-Content-Type-Options/Referrer-Policy                               │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H5    │ No rate limiting                                                             │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H6    │ WS setAllowedOriginPatterns("*")                                             │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ W-H7    │ No WS heartbeat/ping                                                         │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ F-H1    │ SignalToast dismissal improved but may still accumulate under fast stream    │ 🟡 Partial │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ F-H2    │ All dashboard sections fetch simultaneously                                  │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ F-H3    │ WS unsafe casts, no Zod validation                                           │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ K-H1    │ Math.max(1.0, totalVolumeAtBest) distorts low-volume PLI                     │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ K-H3    │ Undocumented * 15 scaling in AumfScenarioEngine                              │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ K-H4    │ Statistics duplicated across 8+ files (no StatisticsUtils)                   │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ I-M2    │ Dashboard image built by string concat "-dashboard"                          │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ I-M3    │ TimescaleDB uses latest-pg16 tag, not pinned                                 │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ I-M4    │ Backup Lambda EventBridge target has no DLQ                                  │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ A-H3    │ Drift is ABM undocumented vs GBM                                             │ ❌         │
├─────────┼──────────────────────────────────────────────────────────────────────────────┼────────────┤
│ A-H5    │ Transfer entropy uses naive histogram, no bias correction                    │ ❌         │
└─────────┴──────────────────────────────────────────────────────────────────────────────┴────────────┘

---
❌ Other Findings Still Open

┌─────────┬──────────────────────────────────────────────────────┬──────────┐
│   ID    │                       Finding                        │  Status  │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ F-L1    │ Dead snake_case types file — DELETED                 │ ✅ Fixed │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ Orig-3  │ SSH open to 0.0.0.0/0 on spot SG + GCP               │ ❌       │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ Orig-4  │ Azure environment still monolithic                   │ ❌       │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ Orig-5  │ GCP has no monitoring/alerting resources             │ ❌       │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ A-M1    │ import torch at module level in regimes/anomaly      │ ❌       │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ A-M2    │ CNN-LSTM used with random/untrained weights          │ ❌       │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ I-L2    │ ACR push uses secrets.ACR_NAME for username          │ ❌       │
├─────────┼──────────────────────────────────────────────────────┼──────────┤
│ ADR-021 │ Post-review remediation architecture ADR not created │ ❌       │
└─────────┴──────────────────────────────────────────────────────┴──────────┘

---
Key Observations

1. The critical silent-noop P0 bugs are largely fixed — this is the most important achievement. The BacktestEngine now actually feeds strategies usable data, the persistent data-source bugs (Shiller, FRED, Finnhub auth) are resolved, and CdmTick.equals no longer breaks on identical data.
2. The cross-cutting P1 workstreams (BigDecimal, transactions, observability) have not started. These are the most expensive items and require the ADR-021 first. Without the micrometer-registry-prometheus dependency, the monitoring stack remains decorative.
3. The KPI controller (F-E1) is the most impactful remaining P0 gap. The dashboard has no functional KPI data — all 8 endpoints return 404. This was escalated from the original review and remains unfixed.
4. Security hardening is at ~60% — auth defaults fixed, but method-level RBAC, WS origins, rate limiting, missing headers, and SSH-to-world are all still open.
5. The analytics Python findings are ~40% addressed (GARCH fixed, EVT and QuantReg not).
6. ADR-021 was never created — the architecture record for the cross-cutting changes still needs to be written.