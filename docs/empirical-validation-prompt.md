# Empirical Validation Implementation Prompt

## Purpose

This document is a self-contained implementation prompt for Phase 3 of the Tickonomics analytics platform verification: **Empirical Validation with Actual Market Data**. Its goal is to answer the ultimate question: *"If someone follows the platform's advice in real financial markets, will the results match what the platform predicts?"*

This phase builds on two completed predecessors:
- **Phase 2** (`docs/analytics-test-verification-plan-final.md`) — Code quality audit and test infrastructure
- **Phase 2.5** (`docs/external-validation-plan.md`) — Mathematical correctness validation against independent oracles (35 steps, found 6 bugs)

Phase 2.5 established that the math is correct (or found bugs where it isn't). Phase 3 asks whether the **models themselves** describe reality, whether the **parameters are calibrated**, and whether the **advice produces expected outcomes** when applied to historical market data.

---

## Prerequisites

### Must be completed before starting Phase 3

1. **All 6 bugs from Phase 2.5 must be fixed** before empirical validation can produce meaningful results. Running empirical tests on broken services wastes time and produces misleading conclusions.

| Bug | Service | Fix Required |
|-----|---------|--------------|
| B1 | `risk_service._estimate_garch` | Fix gradient ascent to update alpha and beta, not just omega. Or replace with `scipy.optimize.minimize` L-BFGS-B over all GARCH(1,1) parameters |
| B2 | `robustness_service.robustness_scan` | Actually apply Sobol-sampled parameters to the evaluation function instead of ignoring them |
| B3 | `econometrics_service._adf_pvalue` | Replace the broken exponential approximation with MacKinnon (1996) response surface approximations (use `statsmodels` implementation as reference) |
| B4 | `q_world_pricer_service.compute_fair_value` | Replace with the standard Hull Ch.31 CIR bond pricing formula. Resolve unit inconsistency |
| B5 | `macro_shock_service` confidence intervals | Compute proper bootstrap or asymptotic standard errors for IRF, then construct CI as `estimate ± 1.96 * SE` |
| B6 | `tournament_service` "LSTM" | Either implement a real LSTM (with trained weights from historical data) or rename the strategy to "noisy_momentum" to avoid misrepresentation |

2. **External validation tests from Phase 2.5 must pass** — confirms the fixes are correct.

3. **TimescaleDB must be running** with ingested data, or historical data must be loaded via the bulk loader (see Step 0 below).

---

## Architecture Context

```
┌─────────────┐     ┌──────────────┐     ┌────────────────┐
│  Ingestion   │────▶│  TimescaleDB │◀────│  Java Backend   │
│  (Java)      │     │  (Port 5432) │     │  (Port 8080)    │
│  FRED/NYFed  │     │              │     │  BacktestEngine │
│  Polygon WS  │     │  tick_data   │     │  HistDataReplay │
└─────────────┘     │  rate_        │     │  WeightOptimizer│
                    │  snapshots    │     └───────┬─────────┘
                    │  ohlcv_1min   │             │
                    │  backtest_    │             │ HTTP
                    │  results      │             │
                    └──────┬────────┘     ┌───────▼─────────┐
                           │              │  Analytics       │
                           │              │  (Port 8001)     │
                           └─────────────▶│  27 services     │
                              data via    │  55 functions    │
                              POST arrays │  42 endpoints    │
                                          └──────────────────┘
```

- The **analytics worker** is a pure computation service — it receives raw numeric arrays in POST bodies and returns computed results.
- The **Java backend** has `HistoricalDataReplay` that reads from TimescaleDB, computes daily returns, and feeds them to the backtest engine.
- The **Python analytics worker** has NO data fetching capability. All data arrives via API requests.
- The **ingestion layer** (Java) feeds FRED rates, NY Fed rates, and Polygon tick data into TimescaleDB in real-time.

---

## Step 0: Data Acquisition Infrastructure

### 0.1 Install data fetching dependencies

Add to `analytics/requirements.txt` and `analytics/pyproject.toml`:
```
yfinance>=0.2.40
pandas>=3.0.0
sqlalchemy>=2.0.0
psycopg2-binary>=2.9.0
```

### 0.2 Create historical data loader

Create `analytics/tests/empirical/data_loader.py` with functions:

```python
def fetch_equity_returns(
    symbol: str = "SPY",
    start: str = "2010-01-01",
    end: str = "2025-12-31",
    frequency: str = "daily"
) -> dict:
    """Download historical equity returns using yfinance.
    Returns: {"dates": list[str], "prices": list[float], "returns": list[float]}
    """

def fetch_treasury_yields(
    maturities: list[str] = ["3M", "6M", "1Y", "2Y", "5Y", "10Y", "30Y"],
    start: str = "2010-01-01",
    end: str = "2025-12-31"
) -> dict:
    """Download US Treasury yields from FRED via yfinance.
    Returns: {"dates": list[str], "maturities": list[float], "yields": list[list[float]]}
    """

def fetch_rate_series(
    series_id: str = "EFFR",
    start: str = "2010-01-01",
    end: str = "2025-12-31"
) -> dict:
    """Download a single FRED series.
    Returns: {"dates": list[str], "values": list[float]}
    """

def fetch_options_snapshot() -> dict:
    """Load a pre-fetched options chain from test fixtures.
    Cannot download historical options data for free.
    Returns: {"positions": list[dict]} suitable for bsm_service.aggregate_gex
    """
```

### 0.3 Define benchmark datasets

Create `analytics/tests/empirical/datasets.py` that defines canonical test periods:

```python
DATASETS = {
    "spy_full": {
        "symbol": "SPY",
        "start": "2010-01-04",
        "end": "2025-12-31",
        "description": "Full SPY history, ~4000 trading days",
    },
    "spy_covid": {
        "symbol": "SPY",
        "start": "2020-01-02",
        "end": "2020-12-31",
        "description": "COVID crash + recovery",
    },
    "spy_taper_tantrum": {
        "symbol": "SPY",
        "start": "2013-05-01",
        "end": "2013-12-31",
        "description": "Taper tantrum rate shock",
    },
    "spy_volatile": {
        "symbol": "SPY",
        "start": "2022-01-03",
        "end": "2022-12-30",
        "description": "2022 rate hike cycle, high vol",
    },
    "tsy_full": {
        "maturities": ["3M", "6M", "1Y", "2Y", "5Y", "10Y", "30Y"],
        "start": "2010-01-04",
        "end": "2025-12-31",
        "description": "Full Treasury yield curve history",
    },
    "tsy_inverted": {
        "maturities": ["3M", "6M", "1Y", "2Y", "5Y", "10Y", "30Y"],
        "start": "2022-06-01",
        "end": "2023-06-30",
        "description": "Yield curve inversion period (2Y>10Y)",
    },
    "effr_full": {
        "series_id": "EFFR",
        "start": "2010-01-04",
        "end": "2025-12-31",
        "description": "Effective Fed Funds Rate full history",
    },
}
```

### 0.4 Download and cache data

Create `analytics/tests/empirical/cache/` directory (gitignored). On first test run, download all datasets via `yfinance` and save as Parquet files. Subsequent runs load from cache.

```python
# In conftest.py:
@pytest.fixture(scope="session")
def spy_returns():
    """Cached SPY daily returns for the full test period."""
    cache_path = Path(__file__).parent / "cache" / "spy_returns.parquet"
    if cache_path.exists():
        df = pd.read_parquet(cache_path)
    else:
        df = download_and_cache("SPY", cache_path)
    return df["returns"].tolist()
```

### 0.5 Data validation

Before using any downloaded data, verify:
- No NaN values in returns
- Returns are in decimal form (e.g., 0.01 for 1%), not percentage
- Dates are trading days only (no weekends/holidays)
- Prices are split/dividend-adjusted (use `yfinance` `Adj Close`)
- Length matches expected trading day count (≈252/year)

---

## Step 1: VaR and CVaR Empirical Backtest

This is the most important empirical test. It is the industry-standard validation method used by bank regulators (Basel II/III).

### 1.1 VaR Exception Count Test (Basel Traffic Light)

**Principle:** If VaR at 99% confidence is correct, actual losses should exceed VaR on approximately 1% of trading days. More exceptions = model is too optimistic. Fewer = model is too conservative.

**Implementation:**

```
Given: SPY daily returns from 2010-01-01 to 2025-12-31 (~4000 trading days)
When:  For each trading day t (starting from day 252):
         - Compute VaR(99%) using the most recent 252 returns (1-year rolling window)
         - Record whether the actual return on day t+1 is below the VaR level
Then:  Count total exceptions over the full period.
       Expected: ~1% of days = ~37 exceptions out of ~3750 out-of-sample days.
       
       Apply Basel traffic light zones:
         GREEN:  0-4 exceptions in last 250 days   → model accepted
         YELLOW: 5-9 exceptions in last 250 days   → model questionable
         RED:    10+ exceptions in last 250 days    → model rejected
```

**Test cases:**
- `test_var_historical_basel_traffic_light` — historical VaR at 99%
- `test_var_parametric_basel_traffic_light` — parametric VaR at 99%
- `test_var_95_basel_traffic_light` — historical VaR at 95% (expect ~5% exceptions)
- `test_var_rolling_500day_window` — use 500-day window instead of 252

**Success criteria:**
- Exception rate for 99% VaR is between 0.5% and 2.0% (allowing for sampling variation)
- Exception rate for 95% VaR is between 3.5% and 6.5%
- No Basel RED zone in any rolling 250-day window

### 1.2 CVaR Consistency Test

**Principle:** CVaR (Expected Shortfall) should equal the average of losses that exceed VaR.

**Implementation:**

```
Given: Same SPY returns and rolling VaR from Step 1.1
When:  For each exception day (where actual loss > VaR):
         - Record the actual loss
Then:  Compute mean(actual losses on exception days).
       Compare to the CVaR reported by the service on the same day.
       
       The service's CVaR should be approximately equal to the 
       mean of actual exception losses.
```

**Success criteria:**
- Average ratio of actual exception loss to reported CVaR is between 0.7 and 1.5
- CVaR always >= VaR in absolute terms (monotonicity invariant)

### 1.3 VaR Regime Sensitivity

**Implementation:**

```
Run the VaR backtest separately on each canonical period:
  - spy_covid (2020): expect HIGH exception rate (model breaks in crisis)
  - spy_volatile (2022): expect MODERATE exception rate
  - spy_full: expect LOW exception rate (long-run average)
```

**Success criteria:**
- COVID period exception rate should be higher than full-period rate (stress test)
- This is expected behavior — VaR models are known to underperform in regime changes
- Document the COVID exception rate as a known limitation

---

## Step 2: GARCH Forecast Accuracy

### 2.1 Realized Volatility Prediction

**Principle:** GARCH forecast variance should predict subsequent realized variance. Compare 5-day GARCH variance forecast to 5-day realized variance (sum of squared returns).

**Implementation:**

```
Given: SPY daily returns from 2010-01-01 to 2025-12-31
When:  For each trading day t (starting from day 504, requiring 2 years of data):
         - Fit GARCH(1,1) on returns[t-504:t] (2-year window)
         - Get 5-day variance forecast
         - Compute 5-day realized variance = sum(returns[t+1:t+6]^2)
Then:  Compute Mincer-Zarnowitz regression:
         realized_vol[t] = alpha + beta * forecast_vol[t] + error
         
       R² of this regression measures forecast quality.
       beta should be close to 1.0.
       alpha should be close to 0.0.
```

**Success criteria:**
- Mincer-Zarnowitz R² > 0.3 (industry standard for daily GARCH is 0.3–0.5)
- beta between 0.5 and 1.5
- Mean Absolute Error (MAE) of forecast vs. realized is lower than naive forecast (historical volatility)

### 2.2 GARCH Persistence Calibration

**Implementation:**

```
Fit GARCH(1,1) on full SPY history.
Extract alpha + beta (persistence).
For equity indices, typical range is 0.85–0.99.

Verify: alpha + beta falls in [0.80, 0.999]
If outside this range, the model is poorly calibrated.
```

**Success criteria:**
- Persistence (alpha + beta) between 0.85 and 0.99
- Unconditional variance from GARCH ≈ sample variance of returns (within 20%)

---

## Step 3: Nelson-Siegel Yield Curve Prediction

### 3.1 Curve Fitting Accuracy

**Principle:** Nelson-Siegel should reproduce observed Treasury yields with low error.

**Implementation:**

```
Given: US Treasury yields at 7 maturities (3M, 6M, 1Y, 2Y, 5Y, 10Y, 30Y)
       for each trading day from 2010-01-01 to 2025-12-31
When:  For each date:
         - Fit Nelson-Siegel on the 7 observed yields
         - Compute fitted yields at the same 7 maturities
         - Compute RMSE = sqrt(mean((observed - fitted)^2))
Then:  Aggregate RMSE across all dates.
```

**Success criteria:**
- Median RMSE across all dates < 15 basis points (0.15%)
- Maximum RMSE on any single date < 50 basis points
- During the inverted period (2022-2023), RMSE may be higher — document this

### 3.2 Yield Curve Forecasting

**Implementation:**

```
For each month-end date t:
  - Fit Nelson-Siegel on yields at time t
  - Use the fitted parameters to predict yields at time t + 22 trading days (1 month ahead)
  - Compare predicted yields to actual yields at t+22
  - Compute RMSE by maturity
```

**Success criteria:**
- 1-month ahead forecast RMSE < 40 basis points for 2Y-10Y range
- Forecast RMSE should be lower than naive forecast (today's yields as prediction)

---

## Step 4: BSM Greeks Hedging Test

### 4.1 Delta Hedging Simulation

**Principle:** If BSM delta is correct, a delta-hedged portfolio should have near-zero P&L over short periods.

**Implementation:**

```
Given: SPY daily prices from 2010-01-01 to 2025-12-31
       Assume: constant implied volatility = 20%, risk-free rate = 5%
When:  For a hypothetical ATM call option with 30-day expiry:
         - At each day t, compute BSM delta
         - Construct a delta-hedged portfolio: long call, short delta shares
         - Record daily P&L of the hedged portfolio
         - After 30 days, record total hedging P&L
Then:  Repeat for overlapping 30-day windows.
       Compute mean and std of hedging P&L.
```

**Success criteria:**
- Mean absolute hedging P&L < 2% of option premium
- Hedging P&L standard deviation < 5% of option premium
- These are relaxed tolerances because real markets have jumps and volatility skew

### 4.2 Put-Call Parity on Real Data

**Implementation:**

```
For multiple (S, K, T, r, sigma) combinations derived from real SPY prices:
  call_price - put_price should ≈ S - K * exp(-r * T)
  Using BSM pricing from the service.
```

**Success criteria:**
- Put-call parity holds to 1e-6 for all test cases (this is a pure math test, not empirical)

---

## Step 5: Fixed Income Duration/Convexity Validation

### 5.1 Duration-Based Price Prediction

**Principle:** Modified duration and convexity should predict bond price changes from yield changes.

**Implementation:**

```
Given: A 10Y Treasury bond with 2% coupon
       Fitted Nelson-Siegel yields for each date
When:  For each date t:
         - Compute modified duration and convexity at the current yield
         - At date t+1, the yield changes by dy
         - Predicted price change = -duration * dy + 0.5 * convexity * dy^2
         - Actual price change = new_price - old_price (from the bond pricing formula)
Then:  Compare predicted vs. actual price change.
```

**Success criteria:**
- Prediction error < 5% of actual price change for yield moves < 10 bps
- Prediction error < 15% of actual price change for yield moves < 50 bps

### 5.2 YTM Solver Accuracy

**Implementation:**

```
For each Treasury security on each date:
  - Price the bond using the Nelson-Siegel fitted yield
  - Feed that price to the YTM solver
  - The solved YTM should equal the Nelson-Siegel yield at that maturity
```

**Success criteria:**
- YTM matches Nelson-Siegel yield to within 1 basis point

---

## Step 6: EVT Tail Risk Validation

### 6.1 Tail VaR Backtest

**Implementation:**

```
Given: SPY daily returns from 2010-01-01 to 2025-12-31
When:  For each trading day t (starting from day 504):
         - Fit EVT/GPD tail on the most recent 504 returns
         - Compute 99.9% tail VaR
         - Record whether actual return on t+1 falls below tail VaR
Then:  Expected exception rate at 99.9% = 0.1%
       Over 3750 days, expect ~3-4 exceptions.
```

**Success criteria:**
- Exception count between 0 and 10 (very wide tolerance due to low frequency)
- COVID period should show higher concentration of exceptions

### 6.2 GPD Fit Quality

**Implementation:**

```
On the full SPY return history:
  - Fit GPD to the 5% tail
  - Generate Q-Q plot of fitted GPD quantiles vs. empirical quantiles
  - Run Kolmogorov-Smirnov test: GPD fit vs. empirical tail data
```

**Success criteria:**
- KS test p-value > 0.05 (cannot reject GPD as a good fit)
- Q-Q plot shows approximate linearity (visual check, documented)

---

## Step 7: Macro Shock Impulse Response Validation

### 7.1 Known VAR Validation

**Implementation:**

```
Given: FRED series EFFR (Fed Funds), and a proxy for market stress (VIX or SPY returns)
When:  Fit a 2-variable VAR(1) on the full history
         Compute impulse response of EFFR to a 1-std-dev shock in VIX
Then:  Verify that:
  - EFFR responds negatively to a VIX shock (flights to quality lower rates)
  - The response decays over horizons (mean-reverting)
```

**Success criteria:**
- Direction of IRF is economically sensible (EFFR drops when VIX spikes)
- IRF decays toward zero over 10+ steps

---

## Step 8: Performance Metrics Validation

### 8.1 Sharpe Ratio on Real Data

**Implementation:**

```
Given: SPY daily returns from 2010-01-01 to 2025-12-31
When:  Compute Sharpe ratio via the service
       Compute Sharpe ratio independently via numpy
Then:  They must match exactly.
```

**Success criteria:**
- Exact match to 1e-6
- Additionally: the computed Sharpe should be in a plausible range for SPY (0.5–1.5 annualized)

### 8.2 Fama-French Factor Regression

**Implementation:**

```
Given: SPY excess returns + Fama-French 3-factor data (MKT-RF, SMB, HML)
       Download from Kenneth French's data library:
       https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/data_library.html
When:  Run the service's fama_french_regression
       Compare coefficients to statsmodels OLS
Then:  Coefficients must match to 1e-4.
       SPY beta to market should be ≈ 1.0 (it's a market proxy)
```

**Success criteria:**
- Coefficients match statsmodels to 1e-4
- Market beta for SPY is between 0.95 and 1.05
- R² > 0.90 (SPY is well-explained by market factor)

---

## Step 9: Econometrics Validation

### 9.1 ADF Test on Known Series

**Implementation:**

```
Test 1: Apply ADF to SPY price levels (non-stationary, random walk)
  → service should report non-stationary (p-value > 0.05)

Test 2: Apply ADF to SPY returns (stationary, mean-reverting)
  → service should report stationary (p-value < 0.05)

Test 3: Compare test statistic to statsmodels.tsa.stattools.adfuller
  → must match to 1e-3
```

**Success criteria:**
- Correct stationary/non-stationary classification for both series
- Test statistic matches statsmodels at matched lag order

### 9.2 Granger Causality on Real Data

**Implementation:**

```
Test 1: EFFR vs. SPY returns — does yesterday's EFFR predict today's SPY return?
  → Document whether causality is detected (no expected direction)

Test 2: SPY returns vs. VIX — does yesterday's SPY return predict today's VIX?
  → Expect: yes (causality from returns to volatility)

Test 3: Compare to statsmodels.grangercausalitytests
  → Direction (causal/not-causal) must agree
```

### 9.3 OLS on Real Data

**Implementation:**

```
Run the service's OLS regression: SPY returns ~ market returns + SMB + HML
Compare all outputs (coefficients, std errors, t-stats, p-values, R²) 
against statsmodels OLS on the same data.
```

**Success criteria:**
- All outputs match to 1e-4

---

## Step 10: Sobol Monte Carlo Convergence

### 10.1 Convergence Rate Test

**Implementation:**

```
Given: Real SPY parameters (S=current price, K=ATM, T=30/365, r=current EFFR, sigma=30-day realized vol)
When:  Run Sobol pricing with n_paths = [256, 512, 1024, 2048, 4096, 8192]
Then:  For each doubling of n_paths, pricing error should decrease by approximately sqrt(2).
       
       Also: compare final price (8192 paths) to analytical BSM.
```

**Success criteria:**
- Error at 8192 paths < 2% of analytical price
- Error decreases monotonically (or nearly so) as n_paths increases

---

## Step 11: Climate/Drift Simulation — Analytical Moment Matching

### 11.1 OU Process Validation

**Implementation:**

```
Given: OU parameters fitted to match SPY return dynamics
       theta=0.5, mu=0.0, sigma=0.15
When:  Run climate_service.simulate_climate with seasonal_amplitude=0, n_paths=10000
Then:  Collect terminal values across all paths.
       Run scipy.stats.kstest against N(mu, sigma^2/(2*theta))
```

**Success criteria:**
- KS test p-value > 0.01 (cannot reject the theoretical distribution)

### 11.2 ABM Drift Validation

**Implementation:**

```
Given: mu=0.0005, sigma=0.01, X0=100, T=1 year, n_steps=252
When:  Run drift_service.simulate_ito with n_paths=10000
Then:  Terminal distribution should match N(X0 + mu*T*252, sigma^2*T*252)
       Run KS test.
```

**Success criteria:**
- KS test p-value > 0.01
- Mean of terminal values within 5% of theoretical mean

---

## Step 12: Sentiment Validation

### 12.1 Known-Text Direction Test

**Implementation:**

```
Given: A curated set of financial texts with known sentiment:
  Positive: "revenue surged 25% beating analyst expectations",
            "the company reported record quarterly profits",
            "strong jobs data boosts market confidence"
  Negative: "company filed for chapter 11 bankruptcy",
            "inflation surged to 40-year high crushing consumer spending",
            "market crashed amid fears of recession"
  Neutral:  "the Federal Reserve held rates unchanged",
            "trading volume was average today"

When:  Run analyze_sentiment on each text
Then:  Positive texts → score > 0.15 (service's positive threshold)
       Negative texts → score < -0.15
       Neutral texts  → score between -0.15 and 0.15
```

**Success criteria:**
- Direction correct for ≥ 80% of test texts
- No positive text is classified as negative or vice versa

---

## Step 13: Anomaly Detection Validation

### 13.1 Known-Event Detection

**Implementation:**

```
Given: SPY daily returns from 2019-01-01 to 2021-12-31
       Known anomaly dates: 2020-02-27 (first COVID crash day),
                            2020-03-09, 2020-03-12, 2020-03-16 (crash cascade),
                            2020-03-24 (largest single-day gain since 1933)
When:  Train autoencoder on 2019 data
       Detect anomalies on 2020 data using a strict threshold
Then:  Check whether the known crash dates are flagged as anomalies.
```

**Success criteria:**
- Recall: ≥ 3 of the 5 known crash dates are flagged as anomalies
- Precision: ≥ 50% of flagged dates correspond to actual extreme market events

---

## Step 14: Regime Detection Validation

### 14.1 Historical Regime Classification

**Implementation:**

```
Given: SPY daily returns from 2010-01-01 to 2025-12-31
When:  Apply regime detection (garch_regime, rahf_regime) to the full history
Then:  Verify that regime transitions correspond to known market events:
  - 2020-02/03: should transition to HIGH_VOL (COVID crash)
  - 2022-01 to 2022-10: should show ELEVATED or HIGH_VOL (rate hike cycle)
  - 2017: should show LOW_VOL or NORMAL (low volatility year)
```

**Success criteria:**
- COVID period classified as HIGH_VOL ≥ 80% of the time
- 2017 classified as LOW_VOL or NORMAL ≥ 70% of the time

---

## Step 15: Stop-Loss Calibration Validation

### 15.1 Grid Search Comparison

**Implementation:**

```
Given: 200 simulated trades with known PnL distribution (mix of winning and losing streaks)
When:  Run markov_stop_service.calibrate_stops with seed=42
       Run independent systematic 100x100 grid search over (stop_loss, take_profit)
Then:  The service's result should be within 20% of the grid optimum's Sharpe.
```

**Success criteria:**
- Service finds parameters that produce Sharpe within 20% of grid optimum
- Service converges (converged=True) within max_iterations

---

## Step 16: FDR Multiple Testing Validation

### 16.1 Replication of Published Results

**Implementation:**

```
Given: A set of simulated p-values where the ground truth is known
       (e.g., 100 tests, 10 true effects at p<0.01, 90 null at p~U(0,1))
When:  Run apply_fdr_correction
Then:  Verify that approximately 10 hypotheses are rejected (the true effects)
       and the false discovery rate is controlled at alpha=0.05.
```

**Success criteria:**
- Power: ≥ 80% of true effects detected (≥ 8 of 10)
- FDR: ≤ 10% of rejections are false discoveries

---

## Step 17: Transfer Entropy Validation

### 17.1 Lead-Lag Detection on Real Data

**Implementation:**

```
Given: SPY returns and VIX changes (same dates)
When:  Compute transfer_entropy(SPY → VIX) and transfer_entropy(VIX → SPY)
Then:  Expect asymmetric information flow:
  - VIX → SPY: positive transfer entropy (volatility predicts returns)
  - SPY → VIX: also positive (returns predict volatility)
  - The direction with stronger TE should be economically interpretable
```

**Success criteria:**
- Both directions show positive TE (information flows both ways)
- At least one direction is statistically significant (is_significant=True)

---

## Step 18: Comprehensive Empirical Report

### 18.1 Generate verdict report

Create `docs/empirical_validation_report.md` with:

```markdown
# Empirical Validation Report

## Executive Summary
- Services tested: 27
- Empirical tests run: XX
- PASS: XX
- FAIL: XX
- INCONCLUSIVE: XX

## Per-Service Results
| Service | Test | Period | Metric | Expected | Observed | Verdict |
|---------|------|--------|--------|----------|----------|---------|
| risk/VaR | Basel traffic light | 2010-2025 | exception rate 99% | ~1% | X.X% | PASS/FAIL |
| risk/VaR | Basel traffic light | COVID 2020 | exception rate 99% | ~1% | X.X% | PASS/FAIL |
| risk/GARCH | Mincer-Zarnowitz R² | 2010-2025 | R² | >0.3 | X.XX | PASS/FAIL |
| fixed_income/NS | curve fit RMSE | 2010-2025 | median RMSE (bps) | <15 | XX.X | PASS/FAIL |
| fixed_income/NS | 1-month forecast RMSE | 2010-2025 | RMSE 2Y-10Y (bps) | <40 | XX.X | PASS/FAIL |
| ... | ... | ... | ... | ... | ... | ... |

## Critical Findings
[Document any service that fails empirical validation]

## Known Limitations
- VaR models are known to underperform during regime changes (documented COVID exception rate)
- Nelson-Siegel may not fit the yield curve during inversions
- Sentiment lexicon is limited to English financial text
- CNN-LSTM regime model uses untrained random weights (unverifiable)
```

### 18.2 Write ADR

Create `docs/adr/ADR-XXX-empirical-validation-methodology.md` documenting:
- Data sources and date ranges used
- Walk-forward methodology (rolling window vs. expanding window)
- Success criteria justification
- Limitations and caveats

---

## File Structure

```
analytics/tests/empirical/
├── __init__.py
├── conftest.py                          # shared fixtures, data caching
├── data_loader.py                       # yfinance/FRED download functions
├── datasets.py                          # canonical test period definitions
├── cache/                               # gitignored Parquet cache
│   └── .gitkeep
├── test_01_var_backtest.py              # Steps 1.1-1.3
├── test_02_garch_accuracy.py            # Steps 2.1-2.2
├── test_03_yield_curve_prediction.py    # Steps 3.1-3.2
├── test_04_bsm_hedging.py               # Steps 4.1-4.2
├── test_05_fixed_income_prediction.py   # Steps 5.1-5.2
├── test_06_evt_tail_backtest.py         # Steps 6.1-6.2
├── test_07_macro_shock_irf.py           # Step 7.1
├── test_08_performance_metrics.py       # Steps 8.1-8.2
├── test_09_econometrics_real.py         # Steps 9.1-9.3
├── test_10_sobol_convergence.py         # Step 10.1
├── test_11_simulation_moments.py        # Steps 11.1-11.2
├── test_12_sentiment_real.py            # Step 12.1
├── test_13_anomaly_events.py            # Step 13.1
├── test_14_regime_historical.py         # Step 14.1
├── test_15_stops_grid.py                # Step 15.1
├── test_16_fdr_power.py                 # Step 16.1
├── test_17_transfer_entropy_real.py     # Step 17.1
└── test_18_report_generation.py         # Step 18.1
```

---

## Execution Order

1. **Fix B1–B6 bugs** (prerequisite)
2. **Run Phase 2.5 external validation** (confirm fixes)
3. **Step 0** — Data infrastructure (yfinance, caching)
4. **Steps 1–2** — VaR/CVaR and GARCH (highest financial impact)
5. **Steps 3–5** — Yield curve, BSM, fixed income
6. **Steps 6–7** — EVT and macro shock
7. **Steps 8–9** — Performance metrics and econometrics
8. **Steps 10–11** — Sobol and simulation validation
9. **Steps 12–15** — Sentiment, anomaly, regime, stops
10. **Steps 16–17** — FDR and transfer entropy
11. **Step 18** — Comprehensive report generation

---

## Dependencies to Add

```
# analytics/requirements.txt additions:
yfinance>=0.2.40
pandas>=3.0.0
sqlalchemy>=2.0.0
psycopg2-binary>=2.9.0
```

---

## Key Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| yfinance API rate limits or changes | Cannot download data | Cache all downloads as Parquet; re-download only when cache expires |
| No free historical options data | Cannot test BSM hedging with real IV surfaces | Use synthetic implied vol from Parkinson estimator on real prices |
| FRED API key required | Cannot fetch rate data | Use yfinance as fallback for FRED series |
| Tests are non-deterministic (stochastic) | Flaky test failures | Use fixed seeds for all stochastic tests; set tolerances wide enough for statistical variation |
| VaR exceptions are rare at 99.9% | Hard to validate EVT with limited data | Use 99% VaR for validation; extrapolate to 99.9% qualitatively |
| Model assumptions violated in real data | Tests fail not because code is wrong but because models don't fit | Document each failure as "model limitation" vs. "code bug"; separate the verdicts |
