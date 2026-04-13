# Project Plan: Funding Market Monitor (High-Performance Edition) — v3 (Complete)

## Objective

Create a high-performance application using Java 25 and Spring Boot 3.4+ to monitor funding
market metrics and correlate them with equity price movements via a sophisticated KPI system.

The system supports two interchangeable concurrency models:

- **Dual-Mode Architecture:** Virtual Threads + Spring MVC (blocking) **and** WebFlux (reactive),
  selectable at startup via a configuration flag.

---

## System Architecture

### 1. Tech Stack

| Layer              | Technology                           | Purpose                                                  |
|:-------------------|:-------------------------------------|:---------------------------------------------------------|
| **Language**       | Java 25 (LTS)                        | Virtual Threads, Structured Concurrency, Scoped Values   |
| **Framework**      | Spring Boot 3.4+                     | Auto-configuration, dependency injection, actuator       |
| **Concurrency**    | Mode A: Virtual Threads + Spring MVC | Simple blocking I/O with cheap threads                   |
| **Concurrency**    | Mode B: WebFlux + Netty              | Event-loop, Flux/Mono, backpressure-aware                |
| **Frontend**       | Next.js 15, TailwindCSS, TypeScript  | Dashboard UI                                             |
| **Charts**         | Lightweight Charts (TradingView)     | Price/KPI time-series panes                              |
| **Charts**         | D3.js                                | Liquidity heatmaps, correlation matrices                 |
| **Time-Series DB** | QuestDB                              | Ingestion of ticks, rates, computed KPIs                 |
| **Relational DB**  | PostgreSQL                           | Configuration, alert rules, signal history (append-only) |
| **Resilience**     | Chronicle Queue                      | Disk-backed overflow for ingestion buffer                |
| **Data Feeds**     | FRED, NY Fed, Polygon.io             | External market and macro data                           |

#### Dual-Mode Concurrency & Consistency

The application starts in **one** of two modes, controlled by `monitor.concurrency-mode`
in `application.yml`.

**Refinement (Consistency Guard):** To prevent behavioral drift, a mandatory **Cross-Stack Consistency Test Suite** is
implemented. It replays identical payloads through both Mode A and Mode B to assert bit-perfect equality in resulting
ILI scores and Signal states.

---

### 2. Database & Data Resilience

| Store          | Data                                                   | Buffer / Retention                   |
|:---------------|:-------------------------------------------------------|:-------------------------------------|
| **QuestDB**    | Raw ticks, rate snapshots, ILI history, Z-score series | Buffer: **1,000,000 events**         |
| **PostgreSQL** | YAML config, alert rules, signal log                   | Indefinite (with periodic archiving) |

**Ingestion Resilience Layer:**

- **In-Memory Buffer:** 1,000,000 events to handle Polygon tick spikes.
- **Disk-Backed Overflow:** If memory buffer utilization > 80%, overflow events are written to a local **Chronicle Queue
  ** to prevent OOM and ensure zero data loss during DB downtime.
- **Schema Migrations:** Flyway for PostgreSQL; a custom **QuestDB Migration Runner** with checksum validation for
  QuestDB.

---

### 3. Logic Layer and Algorithms

#### A. Internal Liquidity Index (ILI) — Tiered Normalization

ILI is a weighted composite of z-score-normalized components.

**Tiered Lookback Strategy:**

- **Macro Components (RRP_Volume, WALCL):** 252-day (1 year) lookback for structural trends.
- **Flow Components (EFFR - IORB Spread):** 60-day lookback for medium-term liquidity cycles.
- **Volatility Components (SOFR_Volatility):** 20-day lookback for high-frequency stress.

**Zero Variance Guard:**

- If standard deviation < `0.0001` over the window, Z-score defaults to `0.0` to prevent `NaN` errors during stagnant
  rate regimes.

#### B. Correlation Engine — Dynamic Lag Analysis

Algorithm: Rolling Pearson Correlation on differenced time series.

**AIC-based Lags:**

- Instead of a fixed lag, the engine uses **AIC (Akaike Information Criterion)** to dynamically select the optimal lag
  order (up to 10 days) for each specific Symbol/Metric pair.
- Preprocessing: ADF test for stationarity; mandatory differencing if non-stationary.

#### C. Signal Generation — Regime Awareness

**Adaptive Thresholding:**

- Cluster ILI volatility into 3 regimes (low/medium/high) using k-means.
- Per-regime, maintain separate percentile thresholds (e.g., 2% in low-vol, 10% in high-vol).

**Data Freshness (Intraday Proxy):**

- Signals generated before the 8:00 AM ET SOFR publication are marked `SPECULATIVE_STALE_MACRO`.
- The system uses 3-month Treasury Bill yields as a real-time proxy for funding pressure between official SOFR updates.

---

### 4. KPI System

| KPI Name                   | Source     | Description                                                             |
|:---------------------------|:-----------|:------------------------------------------------------------------------|
| **Liquidity Stress Index** | Composite  | Normalized distance from Fed Target, adjusted by T-Bill proxy intraday. |
| **Repo/Equity Beta**       | Analytical | Sensitivity of a specific stock to repo rate changes (Rolling OLS).     |
| **RRP Drain Velocity**     | FRED       | Rate of change and acceleration (2nd derivative) of RRP facility.       |
| **Systemic Risk Heatmap**  | NY Fed     | 4-axis z-scored matrix visualized as a D3.js quadrant chart.            |

---

### 5. Configuration System

Hierarchical YAML with hot-reloading (Spring Cloud Config + `@RefreshScope`).

```yaml
monitor:
  concurrency-mode: virtual-threads
  strategies:
    liquidity_pivot:
      ili_weights: { rrp: 0.4, spread: 0.4, vol: 0.2 }
      regime_detection: { enabled: true, clusters: 3 }
      cost_model: { estimated_slippage_bps: 5 }
```

---

## Implementation Breakdown

### Phase 0: API Contracts & Project Scaffolding

- [ ] Define OpenAPI 3.1 & AsyncAPI specs.
- [ ] Multi-module Maven project setup (api, ingestion, computation, persistence, web).
- [ ] Configure Flyway and QuestDBMigrationRunner.

### Phase 1: Ingestion & Resilience Layer

- [ ] Implement `DiskBackedIngestionBuffer` (Chronicle Queue).
- [ ] Implement dual-mode clients (FRED, NY Fed, Polygon) with `Cross-Stack Consistency Suite`.
- [ ] Circuit Breakers (Resilience4j) and Outlier Detection.

### Phase 2: Analytical Engine

- [ ] Implement `TieredNormalizationService` with Zero-Variance Guards.
- [ ] Implement `AICLagSelector` for dynamic lead-lag correlation.
- [ ] Implement `KpiProcessor` with Regime-Aware Signal Generation.
- [ ] Implement `IntradayProxyService` for SOFR/T-Bill tracking.

### Phase 3: Frontend & Visualization

- [ ] Next.js 15 dashboard with TanStack Query.
- [ ] Multi-pane Lightweight Charts (Price vs. ILI).
- [ ] D3.js Correlation Matrix and Systemic Risk Heatmap.

### Phase 4: Backtesting & Calibration

- [ ] Historical Data Replay engine for QuestDB.
- [ ] Weight Optimizer (Bayesian optimization) for ILI recalibration.

### Phase 5: Operations & Validation

- [ ] Gatling Load Tests (1000+ ticks/sec).
- [ ] Property-based tests (jqwik) for calculation invariants.
- [ ] Docker-compose and CI/CD pipelines.
