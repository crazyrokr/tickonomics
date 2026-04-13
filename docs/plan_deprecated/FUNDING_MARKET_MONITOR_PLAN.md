# Project Plan: Funding Market Monitor (High-Performance Edition)

## Objective

Create a high-performance application using Java 25 and Spring Boot WebFlux to monitor funding market metrics and
correlate them with equity price movements via a sophisticated KPI system.

## System Architecture

### 1. Tech Stack

- **Backend:** Java 25 (utilizing Virtual Threads and Structured Concurrency), Spring Boot 3.4+, Spring WebFlux.
- **Frontend:** Next.js 15, TailwindCSS, TypeScript.
- **Visualization:** Lightweight Charts (TradingView) for primary charts, D3.js for complex KPI heatmaps.
- **Database:**
    - **QuestDB:** Time-series ingestion and analytical queries.
    - **PostgreSQL:** Relational data and configuration persistence.
- **Data Feeds:** FRED, NY Fed, Polygon.io.

### 2. Logic Layer & Algorithms

The logic layer operates on a reactive stream processing model using `Flux` and `Mono`.

#### A. Internal Liquidity Index (ILI)

A synthetic KPI calculated as a weighted composite:

- `ILI = (w1 * RRP_Volume) + (w2 * (EFFR - IOER)) - (w3 * SOFR_Volatility)`
- **Logic:** High RRP and stable SOFR indicate high system liquidity.

#### B. Correlation Engine (Lead-Lag Analysis)

- **Algorithm:** Sliding Window Pearson Correlation.
- **Goal:** Identify if funding market spikes (e.g., SOFR jump) lead equity sell-offs.
- **Implementation:** Uses QuestDB's `SAMPLE BY` and `JOIN ON` for high-speed time-alignment of disparate data streams.

#### C. Signal Generation (Z-Score Thresholding)

- Calculate the Z-score of the ILI over a 30-day rolling window.
- **Buy Signal:** ILI Z-score < -2.0 (Extreme fear/liquidity squeeze often precedes pivots).
- **Sell Signal:** ILI Z-score > +2.0 (Excessive exuberance/liquidity peak).

### 3. Reach KPI System

| KPI Name                   | Source     | Description                                                 |
|:---------------------------|:-----------|:------------------------------------------------------------|
| **Liquidity Stress Index** | Composite  | Normalized distance between SOFR and the Target Range.      |
| **Repo/Equity Beta**       | Analytical | Sensitivity of a specific stock to repo rate changes.       |
| **RRP Drain Velocity**     | FRED       | Rate of change in the Overnight Reverse Repo facility.      |
| **Systemic Risk Heatmap**  | NY Fed     | Multi-variable view of tri-party repo vs. GCF repo spreads. |

### 4. Data Source Details

| Source         | Specific Data Points                                                  | Frequency          |
|:---------------|:----------------------------------------------------------------------|:-------------------|
| **FRED**       | EFFR, RRP (RRPONTSYD), TGA Balance, WALCL (Fed Balance Sheet).        | Daily / Weekly     |
| **NY Fed**     | SOFR (99th, 75th percentiles), TGCR, BGCR.                            | Daily (8:00 AM ET) |
| **Polygon.io** | Tickers (AAPL, NVDA, SPY), Aggregates (Min/Hour), Trades (Real-time). | Real-time / 1-min  |

### 5. Configuration System

A hierarchical, YAML-based configuration with hot-reloading capabilities for strategy parameters.

```yaml
monitor:
  watchlists:
    - name: "Magnificent 7"
      symbols: [ "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA" ]
  strategies:
    liquidity_pivot:
      ili_weights: { rrp: 0.4, spread: 0.4, vol: 0.2 }
      z_threshold: 2.0
      cooldown_period: "4h"
  ingestion:
    fred_sync_interval: "12h"
    polygon_ws_enabled: true
```

## Implementation Breakdown

### Phase 1: Reactive Ingestion Layer

- [ ] Configure Spring Boot with **Java 25 Virtual Threads** for non-blocking I/O.
- [ ] Implement `ReactivePolygonClient` using WebFlux `WebSocketClient`.
- [ ] Implement `Scheduled` tasks for FRED/NY Fed with deduplication logic in QuestDB.

### Phase 2: Analytical Engine

- [ ] Develop the `KPIProcessor` to calculate ILI and Z-scores in real-time.
- [ ] Implement QuestDB-optimized SQL queries for lead-lag correlation.
- [ ] Create an `AlertManager` that evaluates signals against the active configuration.

### Phase 3: Frontend & Visualization

- [ ] Next.js dashboard with `SWR` or `React Query` for live state management.
- [ ] Multi-pane Lightweight Charts showing Price vs. ILI.
- [ ] D3.js powered "Liquidity Heatmap" for systemic risk visualization.

### Phase 4: Validation

- [ ] **Unit Tests:** Given SOFR spike, When analyzed, Then Correlation Beta should update correctly.
- [ ] **Integration Tests:** End-to-end flow from Polygon WS -> QuestDB -> Frontend WS.
- [ ] **Load Testing:** Verify 1000+ ticks/sec ingestion with <1% CPU impact on the logic layer.
