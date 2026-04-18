# Consolidated Proposal: Dashboard Enhancement Module

**Status:** Proposal
**Applicability:** Tickonomics v3 (Java 25 Spring Boot + Python FastAPI + TimescaleDB)

## v3 Integration Points

| Track   | Component               | Role                                                                                        |
|---------|-------------------------|---------------------------------------------------------------------------------------------|
| Track 2 | Database Schema         | Historical depth in `daily_kpi_summary` for multi-year QQ-plot analysis                     |
| Track 3 | Python Analytics Worker | Diagnostic API (`/diagnostics/stats`), AT-proxy calculations, FRED macro data processing    |
| Track 4 | Ingestion Layer         | FRED central bank balance sheet data ingestion                                              |
| Track 5 | Computation Engine      | Hendershott/Menkveld AT-proxy computation, macro signal mapping                             |
| Track 7 | Analytics Dashboard     | QQ-plot panel, ACF panel, convergence plot, efficiency gap indicator, monetary policy panel |

---

## Core Proposal: Statistical Integrity Dashboard (Visual Diagnostics)

**Source:** STATISTICAL_INTEGRITY_DASHBOARD
**Reference:** SSRN-5755602 - "Visualizing Fundamental Statistical Principles in Quantitative Finance"

### 1. Objective

Enhance the tickonomics user interface by implementing a suite of Visual Statistical Diagnostics. These visualizations
provide users with empirical evidence of data quality, regime shifts, and the validity of the underlying
Gaussian/Non-Gaussian assumptions used in the ILI and signal generation models.

Current tickonomics provides standard time-series charts. Adding these diagnostics allows users to visually verify if
the current market regime is "Normal" or "Tail-Event Driven," improving trust and decision-making during high-stress
periods.

### 2. Theoretical Basis

The research highlights that while statistical principles like the Law of Large Numbers (LLN) and Central Limit
Theorem (CLT) are fundamental, they are often obscured in purely quantitative dashboards. Key visual tools:

- **Convergence Plots:** Visualize how the mean and volatility stabilize as sample size increases (LLN).
- **QQ-Plots (Quantile-Quantile):** Identify "Heavy Tails" and extreme-value risk (Black Swan potential).
- **ACF (Autocorrelation Function) Comparison:** Contrast the weak linear dependence of returns with the strong
  persistence of volatility proxies (Absolute Returns), highlighting "Volatility Clustering."

### 3. Dashboard Panels

#### Tail Risk Panel (QQ-Plot)

Implement an interactive QQ-Plot for the current ILI distribution. If the plot deviates significantly from the 45-degree
line, the system flags "Heavy Tail Regime."

- Display sample quantiles vs. Normal quantiles.
- Highlight deviations in the tails that indicate Black Swan risk.
- Overlay historical regime classifications.

#### Volatility Persistence Panel (ACF Chart)

Add an ACF Chart showing the autocorrelation of absolute ILI changes. This visually validates the `RegimeDetector`'s
findings on volatility clustering.

- Show lag-N autocorrelations for both raw and absolute returns.
- Contrast weak linear dependence of returns with strong persistence of volatility proxies.
- Enable users to visually confirm volatility clustering patterns.

#### Sample Adequacy Gauge (Convergence Plot)

Use a Convergence Plot (LLN) to show whether the current rolling lookback window (e.g., 60 days for Flow) has reached
statistical stability.

- Display cumulative mean/variance series.
- Indicate whether the current sample size is sufficient for reliable statistical inference.

### 4. Python Analytics Worker Integration

Add a `diagnostic_service.py` to calculate:

- Sample quantiles vs. Normal quantiles for QQ-plots.
- Lag-N autocorrelations for both raw and absolute returns.
- Cumulative mean/variance series for convergence plots.

Expose a `/diagnostics/stats` endpoint returning JSON arrays for QQ and ACF plots.

### 5. Backtesting Integration (Track 8)

Use these visual diagnostics to ensure that quasi-random simulations (from the Riemann Zeta proposal) correctly manifest
heavy tails and clustering, matching real-world liquidity dynamics.

### 6. Database Requirements (Track 2)

Ensure the `daily_kpi_summary` hypertable provides enough historical depth for multi-year QQ-plot analysis.

### 7. Frontend Implementation (Track 7)

Integrate D3.js or Recharts to render the new diagnostic panels. Each panel should be:

- Interactive (zoom, hover tooltips showing exact values).
- Responsive to the current time range selection.
- Exportable as PNG/SVG for reporting.

### 8. Expected Benefits

- **Transparency:** Moves beyond "black box" signals by showing the statistical structure of the market in real-time.
- **Risk Identification:** Visually exposes "Black Swan" risks (Heavy Tails) often hidden in simple volatility scalars.
- **Educational Value:** Makes statistical principles tangible, positioning tickonomics as a professional-grade
  educational and analytical platform.

---

## Complementary Additions

### A. Market Efficiency Diagnostic Suite

**Source:** MARKET_EFFICIENCY_DIAGNOSTIC
**Reference:** `ssrn-2400527.pdf` (Yesha Yadav, 2015)

#### Objective

Yadav (2015) argues that algorithmic trading (AT) creates a disconnect between short-term informational efficiency (
rapid price response) and long-term allocative efficiency (fundamental value). AT models often suffer from "model risk"
and a short-term focus, leading to informational deficits for fundamental investors.

Tickonomics is uniquely positioned to bridge this gap by offering a macro-liquidity index (fundamental) alongside
high-frequency trading signals. By visualizing the "efficiency gap" between these two, the platform offers users a
superior market-intelligence tool.

#### Fundamental-Algorithmic Gap Indicator

Add a `MarketEfficiencyMonitor` to the dashboard that visualizes:

- **Fundamental Component:** The current ILI value (macro liquidity).
- **Algorithmic Component:** High-frequency intraday volume imbalance (from Track 3).
- **Gap:** The drift between the two, highlighting periods where price discovery is driven by AT "noise" vs. fundamental
  macro events.

#### AT-Driven Volume Quantification

Add a section to the `System Health Panel` for transparency reporting:

- Quantify "AT-Driven Volume" per asset by analyzing the ratio of high-frequency message traffic (using the
  Hendershott/Menkveld AT-proxy) to total daily trade volume.
- Clearly categorize assets based on their high-frequency vs. fundamental volume ratios.

#### Validation Plan

- [ ] Develop the `MarketEfficiencyMonitor` UI panel in the Analytics Dashboard.
- [ ] Validate that the "Gap Indicator" correctly flags historical high-volatility events (Flash Crash, Knight Capital).
- [ ] Ensure transparency reports clearly categorize stocks based on their high-frequency vs. fundamental volume ratios.

---

### B. Monetary Policy Sensitivity Panel

**Source:** MONETARY_POLICY_SENSITIVITY
**Reference:** SSRN-2727899

#### Objective

Implement a `MonetaryPolicySensitivityPanel` in the Analytics Dashboard (Track 7) to visualize the impact of central
bank policies (e.g., QE/QT) on funding liquidity. Research indicates that central bank interventions directly impact
risk premiums and the cost of debt for large-scale infrastructure and funding markets.

#### Dashboard Integration

Add a "Monetary Policy Indicators" panel to the Analytics Dashboard that visualizes:

- **Fed Balance Sheet:** Expansion/contraction trends over time.
- **Key Policy Rates:** Federal funds rate, IORB, SOFR spread dynamics.
- **Yield Curve Shape:** Projected flattening/steepening as a macro regime signal.

#### Data Ingestion (Track 4)

Incorporate central bank balance sheet data from FRED into the existing Ingestion Layer. Update the
`System Health Panel` to reflect these macro-data sources.

#### Benefits

- Provides essential macro-context for interpreting ILI and signal status (e.g., distinguishing between liquidity-driven
  stress and policy-induced volatility).
- Enhances the `Systemic Resilience Monitor` by explicitly mapping monetary policy signals to funding liquidity metrics.
- Complements the Statistical Integrity Dashboard by adding a macro overlay that explains regime shifts visible in
  QQ-plots and ACF charts.

---

## Source Proposals

1. **STATISTICAL_INTEGRITY_DASHBOARD** (PRIMARY) - SSRN-5755602 - "Visualizing Fundamental Statistical Principles in
   Quantitative Finance"
2. **MARKET_EFFICIENCY_DIAGNOSTIC** (COMPLEMENTARY) - `ssrn-2400527.pdf` (Yesha Yadav, 2015)
3. **MONETARY_POLICY_SENSITIVITY** (COMPLEMENTARY) - SSRN-2727899
