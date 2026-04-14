# Project Plan: Funding Market Monitor (High-Performance Edition) — v3 (Extended)

## Objective

Create a high-performance application using Java 25 and Spring Boot 3.4+ to monitor funding
market metrics and correlate them with equity price movements via a sophisticated KPI system.

The system supports two interchangeable concurrency models:

- **Dual-Mode Architecture:** Virtual Threads + Spring MVC (blocking) **and** WebFlux (reactive),
  selectable at startup via a configuration flag.

---

## System Architecture

### 1. Tech Stack

| Layer              | Technology                                      | Purpose                                                  |
|:-------------------|:------------------------------------------------|:---------------------------------------------------------|
| **Language**       | Java 25 (LTS)                                   | Virtual Threads, Structured Concurrency, Scoped Values   |
| **Framework**      | Spring Boot 3.4+                                | Auto-configuration, dependency injection, actuator       |
| **Concurrency**    | Mode A: Virtual Threads + Spring MVC (blocking) | Simple blocking I/O with cheap threads                   |
| **Concurrency**    | Mode B: WebFlux + Netty (reactive)              | Event-loop, Flux/Mono, backpressure-aware                |
| **Frontend**       | Next.js 15, TailwindCSS, TypeScript             | Dashboard UI                                             |
| **Charts**         | Lightweight Charts (TradingView)                | Price/KPI time-series panes                              |
| **Charts**         | D3.js                                           | Liquidity heatmaps, correlation matrices                 |
| **Time-Series DB** | QuestDB                                         | Ingestion of ticks, rates, computed KPIs                 |
| **Relational DB**  | PostgreSQL                                      | Configuration, alert rules, signal history (append-only) |
| **Resilience**     | Chronicle Queue                                 | Disk-backed overflow for high-volume ingestion           |
| **Data Feeds**     | FRED, NY Fed, Polygon.io                        | External market and macro data                           |

#### Database Boundary

| Store          | Data                                                                                             | Retention                                        |
|:---------------|:-------------------------------------------------------------------------------------------------|:-------------------------------------------------|
| **QuestDB**    | Raw ticks, rate snapshots, ILI history, Z-score series, correlation outputs                      | Tick data: 90 days; daily aggregates: indefinite |
| **PostgreSQL** | YAML config snapshots, user preferences, alert rules, signal log (append-only), backtest results | Indefinite (with periodic archiving)             |

**Refinement (Schema Safety):**

- **Flyway** for PostgreSQL migrations.
- **QuestDB Migration Runner:** A custom implementation with checksum validation and execution tracking to ensure schema
  consistency across environments, mirroring Flyway's behavior for time-series tables.

#### Dual-Mode Concurrency Design

The application starts in **one** of two modes, controlled by `monitor.concurrency-mode`
in `application.yml`.

**Refinement (Consistency Guard):**
To prevent behavioral drift between MVC and WebFlux modes, a mandatory **Cross-Stack Consistency Test Suite** is
implemented. It replays identical payloads through both Mode A and Mode B to assert bit-perfect equality in resulting
ILI scores and Signal states.

---

### 2. Logic Layer and Algorithms

#### A. Internal Liquidity Index (ILI) — Tiered Normalization

A synthetic KPI calculated as a weighted composite of z-score-normalized components.

**Refinement (Tiered Lookback):**
Fixed windows often fail across different data frequencies.

- **Macro Components (RRP_Volume, WALCL):** 252-day (1 year) lookback for structural trends.
- **Flow Components (EFFR - IORB Spread):** 60-day lookback for medium-term liquidity cycles.
- **Volatility Components (SOFR_Volatility):** 20-day lookback for high-frequency stress detection.

**Refinement (Zero Variance Guard):**
In periods of stagnant rates, if standard deviation < `0.0001` over the window, the Z-score for that component defaults
to `0.0` instead of `NaN` or `Infinity`.

#### B. Correlation Engine — Dynamic Lag Analysis

Algorithm: Rolling Pearson Correlation on differenced (stationary) time series.

**Refinement (AIC-based Lags):**
Instead of a fixed lag, the engine will use **AIC (Akaike Information Criterion)** to dynamically select the optimal lag
order (up to 10 days) for each specific Symbol/Metric pair.

Preprocessing:

1. All raw series are converted to daily changes (first differences or log-returns) to ensure stationarity.
2. Augmented Dickey-Fuller (ADF) test is applied at startup.

#### C. Signal Generation — Regime Awareness

Replaces fixed Z-score thresholds with a percentile-rank system that adapts to the current volatility regime.

**Refinement (Signal Freshness & Proxies):**

- **Intraday Proxy:** Between official SOFR updates, the system uses 3-month Treasury Bill yields as a proxy for
  intraday funding pressure shifts.
- **Status Flags:** Signals generated before the 8:00 AM ET publication are marked `SPECULATIVE_STALE_MACRO`.

**Regime Detection:**

- Cluster ILI volatility into 3 regimes (low/medium/high) using k-means.
- Per-regime, maintain separate percentile thresholds.

---

### 3. KPI System

| KPI Name                   | Source     | Calculation                                                                          | Output                        |
|:---------------------------|:-----------|:-------------------------------------------------------------------------------------|:------------------------------|
| **Liquidity Stress Index** | Composite  | `(SOFR - Target_Upper) / Target_Range`, z-scored, adjusted by T-Bill proxy intraday. | Scalar: positive = stress     |
| **Repo/Equity Beta**       | Analytical | Rolling OLS sensitivity of specific stocks to repo rate changes.                     | `{ beta, r_squared, window }` |
| **RRP Drain Velocity**     | FRED       | Rate of change and acceleration (2nd derivative) of RRP facility.                    | `{ velocity, acceleration }`  |
| **Systemic Risk Heatmap**  | NY Fed     | 4-axis z-scored matrix visualized as a D3.js quadrant chart.                         | JSON matrix + quadrant labels |

---

### 4. Data Source Details

| Source      | Data Points                 | Frequency           | Series / Endpoint                               |
|:------------|:----------------------------|:--------------------|:------------------------------------------------|
| **FRED**    | EFFR, RRP, TGA, WALCL, IORB | Daily/Weekly        | `EFFR`, `RRPONTSYD`, `WTREGEN`, `WALCL`, `IORB` |
| **NY Fed**  | SOFR (all pctl), TGCR, BGCR | Daily, T+1 8 AM ET  | `/api/v2/sofr`, `/api/v2/tgcr`, `/api/v2/bgcr`  |
| **Polygon** | Tick & Aggregates           | Real-time WS / Poll | `wss://socket.polygon.io/...`                   |

#### Missing Data & Resilience Policy

| Scenario           | Strategy                                                                                                                                                                |
|:-------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **QuestDB Outage** | **High-Volume Ingestion Buffer:** Increased to 1,000,000 events. If memory utilization > 80%, overflow events are written to a local **Chronicle Queue** (disk-backed). |
| FRED/NY Fed gap    | Forward-fill up to 3 business days. After 3 days, mark `STALE`.                                                                                                         |
| Outlier detection  | Any rate move > 50 bps/day flagged `SUSPECT`, requires manual confirmation.                                                                                             |

---

### 5. Configuration System

YAML-based with schema validation and hot-reloading.

```yaml
monitor:
  concurrency-mode: virtual-threads
  strategies:
    liquidity_pivot:
      ili_weights: { rrp: 0.4, spread: 0.4, vol: 0.2 }
      regime_detection: { enabled: true, clusters: 3 }
      cost_model: { estimated_slippage_bps: 5 }
  ingestion:
    buffer_capacity: 1000000
    chronicle_queue_path: "./data/overflow"
```

---

### 6. Error Handling and Resilience

| Pattern               | Tool             | Scope                                                    |
|:----------------------|:-----------------|:---------------------------------------------------------|
| **Circuit Breaker**   | Resilience4j     | Each external API. Opens after 5 consecutive failures.   |
| **Consistency Suite** | Custom           | Mandatory cross-stack validation for MVC/WebFlux parity. |
| **NaN Guard**         | CalculationGuard | Every KPI passes `Double.isFinite()` check.              |

---

## Implementation Breakdown (Updated)

### Phase 0: API Contracts and Project Scaffolding

- [ ] Define OpenAPI 3.1 & AsyncAPI specs.
- [ ] Multi-module Maven setup with Flyway and **QuestDBMigrationRunner**.
- [ ] Implement the **Cross-Stack Consistency Test Suite** scaffold.

### Phase 1: Ingestion & Resilience Layer

- [ ] Implement `DiskBackedIngestionBuffer` using **Chronicle Queue**.
- [ ] Implement dual-mode clients with **Consistency Guard** validation.
- [ ] Circuit Breakers and Outlier Detection logic.

### Phase 2: Analytical Engine

- [ ] Implement **TieredNormalizationService** (252d/60d/20d) with Zero-Variance Guards.
- [ ] Implement **AICLagSelector** for dynamic lead-lag correlation.
- [ ] Implement **IntradayProxyService** (SOFR/T-Bill tracking).
- [ ] Signal Generator with Regime-Aware thresholds and freshness flags.

### Phase 3: Frontend and Visualization

- [ ] Next.js 15 dashboard with TanStack Query.
- [ ] Multi-pane Lightweight Charts and D3.js Correlation Matrix.

### Phase 4: Backtesting Framework

- [ ] Historical Data Replay engine and Bayesian Weight Optimizer.

### Phase 5: Operations & Validation

- [ ] Gatling Load Tests (1000+ ticks/sec).
- [ ] Property-based tests (jqwik) for calculation invariants.

---

## Architecture Design Record (Updated)

| ADR     | Title                                                | Status   |
|:--------|:-----------------------------------------------------|:---------|
| ADR-001 | Dual-mode concurrency (Virtual Threads vs WebFlux)   | Proposed |
| ADR-002 | QuestDB Migration Runner for schema safety           | **NEW**  |
| ADR-003 | Tiered Normalization windows for macro/flow parity   | **NEW**  |
| ADR-004 | AIC-based Dynamic Lag Selection                      | **NEW**  |
| ADR-005 | Chronicle Queue for high-volume ingestion resilience | **NEW**  |
| ADR-006 | Intraday Proxy (T-Bills) for SOFR gap-filling        | **NEW**  |
