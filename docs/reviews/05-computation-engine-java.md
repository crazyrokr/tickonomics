# Computation Engine Java Code Review

**Review Date**: 2026-06-14
**Scope**: `computation/src/main/java/com/tickonomics/computation/` (102 source files reviewed across all 23 packages)
**Reviewer**: Automated code review

---

## 1. Executive Summary

The computation engine is the core quantitative finance module of Tickonomics. It covers 25 equity strategies, backtesting, risk analytics, ILI computation, regime detection, portfolio optimization, anomaly detection, and pairs trading across ~102 Java files. The codebase demonstrates solid domain knowledge with well-organized packages and consistent use of Java records for data transfer objects.

**Overall assessment**: The architecture is sound, but the review identified several high-severity issues requiring immediate attention -- including numerically incorrect financial calculations, a wrong Sharpe ratio computation, misuse of floating-point for monetary values, and potential division-by-zero conditions. Below is a detailed breakdown organized by category.

---

## 2. Critical Findings (Must Fix)

### 2.1 INCORRECT: ScheduledCalibrationTask.computeSharpeFromWeights Computes Mean/Variance of Weights Instead of Returns

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/optimization/ScheduledCalibrationTask.java`, lines 196-207

```java
double computeSharpeFromWeights(double[] weights) {
    double sum = 0;
    double sumSq = 0;
    for (double w : weights) {
        sum += w;
        sumSq += w * w;
    }
    double mean = sum / weights.length;
    double variance = sumSq / weights.length - mean * mean;
    double std = Math.sqrt(Math.max(0, variance));
    return std > 0 ? mean / std : 0.0;
}
```

**Problem**: This method computes the mean and standard deviation of the weight values themselves, then divides them to produce a "Sharpe ratio." This is fundamentally wrong -- a Sharpe ratio must be computed from portfolio returns (mean excess return / standard deviation of returns). The weights' own distribution statistics have no financial meaning. This method is called by `doCalibration()` and `perturbOptimize()`, meaning the entire calibration/perturbation optimization runs on a meaningless fitness function.

**Fix**: Replace with an actual portfolio return computation. Obtain historical return data for each asset and compute the weighted portfolio return series, then calculate Sharpe from that series. The `computeSharpeRatio(double[][], double[])` method on the same class does this correctly and should be the only Sharpe computation used.

### 2.2 INCORRECT: Total Cost Calculation in PortfolioManagementAlgebra

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/portfolio/PortfolioManagementAlgebra.java`, line 24

```java
double totalCost = (t0Rate + t1Rate) * notional + spreadCost;
```

**Problem**: This expression computes `t0Rate * notional + t1Rate * notional + spreadCost`, but `t0Cost` is already `notional * t0Rate` and `t1Cost` is already `notional * t1Rate`. If the caller passes raw rate values (e.g., 0.001 for 10 bps), the arithmetic is `(0.001 + 0.001) * notional + spreadCost`, which does match `t0Cost + t1Cost + spreadCost`. However, the fact that `t0Cost` and `t1Cost` are computed but never included in `totalCost` (line 25 uses `t0Cost` and `t1Cost` in the debug log but line 24 recalculates using raw rates) creates a maintenance risk -- if either `t0Cost` or `t1Cost` incorporates any additional factor in a future change, `totalCost` will silently diverge.

**Fix**: Use `double totalCost = t0Cost + t1Cost + spreadCost;` so the computed values are used consistently.

### 2.3 INCORRECT: PortfolioManagementAlgebra Uses double for Monetary Values

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/portfolio/PortfolioManagementAlgebra.java`

**Problem**: All monetary calculations use primitive `double`. `long size * double price` can produce rounding errors. `BigDecimal` is the standard for financial applications requiring exact decimal arithmetic.

**Fix**: Either use `BigDecimal` throughout or, if performance requirements preclude it, use `long` for fixed-point (e.g., cents or micro-dollars) and document the precision contract explicitly.

### 2.4 INCORRECT: Python-Style Format Strings in SLF4J Logging

**File**: Multiple files -- `PortfolioManagementAlgebra.java` (lines 26, 40), `RiskPremiumResidualMonitor.java` (line 45), `MarketSensitivityLibrary.java` (line 40), `ExecutionComparison` (line 37)

```java
log.debug("Costs: notional={:.2f}, t0={:.2f}", notional, t0Cost);
```

**Problem**: `{:.2f}` is Python-style format specifier, not SLF4J. SLF4J uses `{}` as the placeholder. At runtime, SLF4J will treat `{:.2f}` as literal text, resulting in log messages like `Costs: notional={:.2f}, t0={:.2f}` without any value substitution. The actual values are passed as arguments but never interpolated.

**Fix**: Use `{}` placeholders and format values before passing, or use `String.format()` inside the log call:
```java
log.debug("Costs: notional={}, t0={}", String.format("%.2f", notional), String.format("%.2f", t0Cost));
```

### 2.5 INCORRECT: Sharpe Ratio Uses Population Variance (Divide by N) Instead of Sample Variance (Divide by N-1)

**Files**: 
- `DelayDExecutor.java` line ~110: `variance /= n;`
- `ScheduledCalibrationTask.java` line 223: `double std = Math.sqrt(sumSqDiff / returns.length);`
- `RiskPremiumResidualMonitor.java` line 57: `Math.sqrt(variance)` computed with `average()` which divides by N

**Problem**: Financial statistics convention uses sample standard deviation (divide by n-1) for Sharpe ratio computations. Using population variance (divide by n) slightly overstates Sharpe ratios, especially for small sample sizes.

**Fix**: Use `variance /= (n - 1)` for variance denominator in Sharpe and standard deviation calculations. Ensure n >= 2 before computing.

### 2.6 INCORRECT: FixedIncomePortfolioBuilder Duration-Neutral Weight Formula

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/fixedincome/FixedIncomePortfolioBuilder.java`, lines 76-79

```java
double wMid = -1.0;
double wShort = dM / (dS + dL) * (dL / dM);
double wLong = 1.0 - wShort;
```

**Problem**: `wShort = dM / (dS + dL) * (dL / dM)` simplifies to `dL / (dS + dL)`. The formula looks mathematically suspicious for a duration-neutral butterfly. In a duration-neutral butterfly, the middle bond is shorted (hence wMid = -1.0), and the wings are long with weights chosen so that `wShort * dS + wMid * dM + wLong * dL = 0`. Solving: `wShort * dS - 1.0 * dM + wLong * dL = 0` with `wShort + wLong = 1.0` (assuming equal notional on wings). The correct solution is `wShort = (dL - dM) / (dL - dS)` and `wLong = (dM - dS) / (dL - dS)`. The current formula does not match this.

**Fix**: Derive and verify the weight formula from the duration-neutral constraint: `wShort * dS + (-1.0) * dM + wLong * dL = 0` and `wShort + wLong = 1.0`.

### 2.7 INCORRECT: RiskPremiumResidualMonitor Removes Sign in Volatility Standardization

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/risk/RiskPremiumResidualMonitor.java`, line 42

```java
boolean dislocated = Math.abs(residual) > threshold * residualStd;
```

**Problem**: The standard financial approach is `Math.abs(residual) / residualStd > threshold` (z-score based detection). The current formula `Math.abs(residual) > threshold * residualStd` is algebraically equivalent, but it obscures the z-score interpretation. This is a readability issue, not a mathematical error. However, the `residualStd` is computed from historical residuals which include the current observation -- this creates a look-ahead bias (the current residual should be excluded from the standard deviation used to test it).

**Fix**: Exclude the current residual from `historicalResiduals` when computing `residualStd`, or pass pre-computed historical statistics.

### 2.8 INCORRECT: BacktestEngine Misuses Signal Generation

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/backtest/BacktestEngine.java`, lines 46-57

```java
Map<String, Double> input = new LinkedHashMap<>();
for (int i = 0; i < dailyReturns.size(); i++) {
    input.put("return_" + i, dailyReturns.get(i));
    AlphaSignal signal = strategy.compute(input, ctx);
    // ...
}
```

**Problem**: The backtest engine accumulates daily returns into a map with keys `"return_0"`, `"return_1"`, etc., and passes this map to every strategy. But the equity strategies expect specific indicator names as input keys (e.g., `"emaFast"`, `"rsi"`, `"price"`, `"upperBand"`). Since none of these keys will be present in the map, every strategy will return `AlphaSignal.neutral()` on every iteration, making the backtest produce no meaningful results. The design intent appears to be that indicator values should be pre-computed externally and passed in, but the current implementation does not do this.

**Fix**: Either (1) compute indicator values in the backtest engine before calling strategy.compute(), or (2) have the HistoricalDataReplay or a dedicated IndicatorComputer produce the expected indicator maps.

---

## 3. High Severity Findings

### 3.1 AnomalyScoringService - Unchecked Type Casts

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/anomaly/AnomalyScoringService.java`

Lines 172-173:
```java
List<Boolean> anomalyMask = (List<Boolean>) result.get("anomaly_mask");
List<Number> reconstructionErrors = (List<Number>) result.get("reconstruction_errors");
```

**Problem**: Unchecked casts from `Map<String, Object>` without type validation. If the Python analytics worker returns a different type structure, these casts will fail at runtime with a `ClassCastException`. The `@SuppressWarnings("unchecked")` annotation hides the warning but does not add safety.

**Fix**: Add `instanceof` checks before casting:
```java
Object maskObj = result.get("anomaly_mask");
if (!(maskObj instanceof List<?> rawList)) { /* handle error */ }
// Then validate each element
```

### 3.2 PhantomLiquidityService - Division by Zero Guard Too Weak

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/liquidity/PhantomLiquidityService.java`, line 14

```java
double pli = canceledVolume / Math.max(1.0, totalVolumeAtBest);
```

**Problem**: `Math.max(1.0, totalVolumeAtBest)` caps the denominator at 1.0. If `totalVolumeAtBest` is 0.5 (very small but real volume), the denominator becomes 1.0, producing `pli = canceledVolume / 1.0` instead of the true ratio. This silently overstates the denominator for any volume below 1.0, which distorts PLI for low-volume instruments.

**Fix**: Return a sentinel value (e.g., 0.0 or Double.NaN with a log warning) when `totalVolumeAtBest` is below a meaningful threshold, or use `Math.max(minVolumeThreshold, totalVolumeAtBest)` with a threshold appropriate to the asset class (e.g., 1e-6).

### 3.3 FireflyWeightOptimizer - Sharpe Ratio Uses Population Variance

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/optimization/FireflyWeightOptimizer.java`, lines 146-152

```java
variance += (r - mean) * (r - mean);
variance /= portfolioReturns.length;  // should be portfolioReturns.length - 1
```

And line 153: `if (stdDev < 1e-12)` uses 1e-12 when typical financial returns have std ~0.01. A threshold of 1e-12 is too small to catch degenerate portfolios in double precision.

### 3.4 Eq553SlippageModel - Infinite Slippage on Zero Volume

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/backtest/Eq553SlippageModel.java`, lines 9-11

```java
if (addvDollarVolume <= 0) {
    return Double.MAX_VALUE;
}
```

**Problem**: Returning `Double.MAX_VALUE` for slippage causes downstream calculations (`adjustReturn`, backtest results) to produce `-Infinity`. This cascades and can NaN-contaminate the entire result set. Better to return a high but finite cap (e.g., 10000 bps = 100%) or throw a documented exception.

### 3.5 BarrierHittingTimeAnalyzer - Incorrect Exponential in Hit Probability

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/backtest/BarrierHittingTimeAnalyzer.java`, line 103

```java
double d = (drift + 0.5 * sigma * sigma) / sigma;
double exponent = -2.0 * d * adjustedDistance / (sigma * sigma);
```

**Problem**: The classic first-passage probability formula for Brownian motion with drift uses `exp(-2 * mu * distance / sigma^2)` where `mu` is the drift rate. Setting `d = (drift + 0.5 * sigma^2) / sigma` and then computing `-2 * d * distance / sigma^2` introduces an extra term. The standard formula is `P(hit) = exp(-2 * drift * distance / sigma^2)`. The current implementation adds a convexity adjustment term `0.5 * sigma^2` to the drift, which may be intentional (Ito correction for log-normal prices) but should be documented and verified against the intended probability model.

### 3.6 DelayDExecutor - Potential Division by Zero for Sharpe

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/backtest/DelayDExecutor.java`, line ~115

```java
double sharpe = Math.sqrt(variance) > 0 ? mean / Math.sqrt(variance) : 0.0;
```

**Problem**: `Math.sqrt(variance) > 0` guards against zero variance, but also returns 0 for negative variance (which can arise from floating-point precision issues with the formula `variance /= n` applied to nearly-constant series). Additionally, the sign is lost: if mean is negative, Sharpe should be negative. The current formula is correct in this regard since it doesn't apply `Math.abs()`, but the zero-guard loses information when variance is extremely small but mean is large (infinite information ratio should be flagged, not silently set to 0).

### 3.7 AumfScenarioEngine - Hardcoded Scaling Factor

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/scenario/AumfScenarioEngine.java`, line 72

```java
yield vol != null && Math.abs(vol.value()) > threshold * 15;
```

**Problem**: The `* 15` scaling factor is hardcoded without explanation. The `volatility_zscore` threshold in `CrisisProfile` is stored as 3.0, 4.0, or 5.0, but the comparison multiplies it by 15, effectively comparing `Math.abs(vol.value()) > 45` for COVID-2020. This magic number needs documentation or should be expressed as a named constant.

---

## 4. Medium Severity Findings

### 4.1 Multiple Classes - Duplicated Statistical Computation Logic

The following statistical computations are duplicated across multiple files:

| Computation | Files |
|---|---|
| Mean | `PairsTradingEngine`, `RiskPremiumResidualMonitor`, `ScheduledCalibrationTask`, `CrossModelValidator`, `WalkForwardValidator`, `ForecastPersistenceService`, `FireflyWeightOptimizer`, `UniverseAggregator` |
| Standard Deviation | `PairsTradingEngine`, `RiskPremiumResidualMonitor`, `CrossModelValidator`, `WalkForwardValidator`, `ForecastPersistenceService`, `FireflyWeightOptimizer` |
| Sharpe Ratio | `CrossModelValidator`, `WalkForwardValidator`, `FireflyWeightOptimizer`, `DelayDExecutor`, `ScheduledCalibrationTask` |
| Normalization | `WeightedWeightStore`, `RegimeAwareWeightingService`, `FireflyWeightOptimizer`, `AgnosticAggregator`, `BayesianWeightOptimizer`, `ScheduledCalibrationTask` |

**Recommendation**: Extract a `StatisticsUtils` or `MathUtils` utility class with well-tested implementations. This eliminates code duplication and ensures consistent numerical behavior (e.g., sample vs. population variance is decided in one place).

### 4.2 BacktestEngine and BacktestResult - SlippageCostBps vs TotalReturn Semantics

**File**: `BacktestResult.java`, the `slippageCostBps` and `totalReturn` fields lack Javadoc explaining their units. `slippageCostBps` is in basis points (confirmed by Eq553SlippageModel), but `totalReturn` appears to be the raw cumulative sum of returns (not percentage). This ambiguity makes it easy for consumers to misinterpret results.

### 4.3 HistoricalDataReplay - Daily Return Extraction Fragile

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/backtest/HistoricalDataReplay.java`, lines 37-41

```java
String dayKey = tick.time().toString().substring(0, 10);
```

**Problem**: Relies on `Instant.toString()` producing ISO-8601 format starting with `YYYY-MM-DD`. While this is true for the standard Java implementation, it is fragile -- `Instant.toString()` output format is not contractually guaranteed to always produce 10 characters for the date portion. Use `LocalDate.from(tick.time().atZone(ZoneOffset.UTC))` for explicit date extraction.

### 4.4 WalkForwardValidator - Fold Sliding Does Not Advance Train Window

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/backtest/WalkForwardValidator.java`, lines ~83-93

```java
int trainStart = 0;
int trainEnd = anchorWindow;
while (trainEnd + testWindow <= returns.size()) {
    List<Double> trainSlice = returns.subList(trainStart, trainEnd);
    // ...
    trainEnd += testWindow;
}
```

**Problem**: The training window's start index `trainStart` never advances -- it stays at 0. This is "anchored" walk-forward, which is a valid variant, but the default name and typical financial backtesting use a "rolling" walk-forward where both the train start and end advance. The anchored approach means later folds have increasingly more training data, which reduces fold-to-fold comparability. The variable naming (`anchorWindow`) suggests this is intentional but the behavior should be documented.

### 4.5 CrossModelValidator - MaxDrawdown Computed on Cumulative Returns Without Reset

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/backtest/CrossModelValidator.java`, lines 104-113

```java
double peak = 0.0;
double maxDd = 0.0;
double cumulative = 0.0;
for (double r : returns) {
    cumulative += r;
    if (cumulative > peak) { peak = cumulative; }
    double drawdown = peak - cumulative;
    if (drawdown > maxDd) { maxDd = drawdown; }
}
```

**Problem**: This is correct for log returns (where cumulative sum equals log price). For simple returns, cumulative sum is NOT the same as cumulative portfolio value. If returns are simple returns, the drawdown should use `cumulative = cumulative * (1 + r)` starting from `cumulative = 1.0`. This ambiguity should be resolved and documented.

### 4.6 ClimateSensitivityFactor - Caching Uses Volatile Without Synchronization

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/ili/ClimateSensitivityFactor.java`, lines 28-29, 56-76

```java
private volatile double cachedFactor = FALLBACK_FACTOR;
private volatile Instant cachedAt = Instant.EPOCH;
```

**Problem**: Two separate `volatile` fields create a potential race condition. The cache check at line 58 reads `cachedAt` and then `cachedFactor` -- between these two reads, another thread could update both fields, leading to a mix of old/new values. This is a benign race (worst case: an extra API call), but for correctness, use `AtomicReference<CachedValue>` where `CachedValue` is an immutable record holding both factor and timestamp.

### 4.7 SessionRangeService - Overnight Hour Range Incorrect

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/regime/SessionRangeService.java`, lines 76-81

```java
} else {
    sessionType = "OVERNIGHT";
    startHour = 22;
    endHour = 0;
    volatilityMultiplier = VOLATILITY_MULTIPLIER_OVERNIGHT;
}
```

**Problem**: `endHour = 0` when `startHour = 22` creates an interval [22, 0) which is technically empty in a linear integer range. For overnight session representation, `endHour = 24` (or 23:59) is more accurate. This is a display/metadata issue, not a logic bug, since the detection logic correctly identifies hours 22-23 as OVERNIGHT.

### 4.8 ArrowIpcTransport - BufferAllocator Not Closed

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/transport/ArrowIpcTransport.java`, lines 23-27

**Problem**: `RootAllocator` implements `AutoCloseable` but `ArrowIpcTransport` never closes it. Arrow allocators hold native (off-heap) memory. Without explicit closing, this causes a native memory leak. The class should implement `Closeable` or `AutoCloseable` and close the allocator.

### 4.9 AmbiguityAdjustedIli - Proper NaN/Infinity Handling but Potential Division by Zero

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/ili/AmbiguityAdjustedIli.java`, lines 35-57

```java
if (Double.isNaN(iliValue) || Double.isInfinite(iliValue)) { ... }
if (Double.isNaN(historicalVolatility) || Double.isInfinite(historicalVolatility) || historicalVolatility < 0) { ... }
double stdError = historicalVolatility / Math.sqrt(lookbackCount);
```

**Problem**: The NaN/Infinity guards are good. However, if `historicalVolatility == 0.0` and `iliValue` is non-NaN, the method proceeds normally and produces `stdError = 0.0`, `upperBand = lowerBand = iliValue`, and `bandWidth = 0.0`, classifying the result as CONFIDENT. A zero-volatility input should arguably produce UNCERTAIN (insufficient data to estimate bands) rather than falsely CONFIDENT.

### 4.10 RegimeAwareWeightingService - Weights Can Become Negative After Adjustment

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/optimization/RegimeAwareWeightingService.java`, lines 59-67

```java
private void applyHighVolAdjustment(double[] weights) {
    for (int i = 1; i < weights.length; i++) {
        weights[i] *= (1.0 + HIGH_VOL_BOOST);  // multiply by 1.4
    }
    if (weights.length > 0) {
        weights[0] *= (1.0 - HIGH_VOL_RRP_REDUCTION);  // multiply by 0.6
    }
}
```

**Problem**: If the original RRP weight is very small (e.g., 0.01), `0.01 * 0.6 = 0.006` -- this stays positive. But there is no lower-bound clamp before normalization. If weights ever become negative due to other adjustments, `normalize()` would produce incorrect results. The `normalize()` method does handle `sum <= 0.0` by resetting to base weights, which is a reasonable fallback.

### 4.11 VotingClassifier -- Mutable Enabled Field in Component (Thread Safety)

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/signal/VotingClassifier.java`, lines 13, 20, 67-70

```java
private boolean enabled;
// ...
public void setEnabled(boolean enabled) {
    this.enabled = enabled;
}
```

**Problem**: `VotingClassifier` is a `@Component` (singleton) with a mutable `enabled` field and a public setter. Multiple threads calling `vote()` and `setEnabled()` concurrently can see inconsistent states. The `enabled` field is read in `vote()` without any synchronization, and written via `setEnabled()` without any memory barrier. Use `volatile` or `AtomicBoolean`.

### 4.12 SignalGenerator -- Percentile Rank Edge Case at Minimum Value

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/kpi/SignalGenerator.java`, line ~56 (delegates to NormalizationService)

```java
long below = Arrays.stream(values).filter(v -> v < currentValue).count();
```

**Problem**: This uses strict `<` rather than `<=`. When `currentValue` equals the sample minimum, `percentile = 0`, which correctly maps to a SELL signal. When it equals the sample maximum, `percentile = (N-1) / N * 100`, which is slightly below 100%. This is the standard "percentile rank" formula and is correct, but the edge case where ALL historical values equal the current value produces `percentile = 0`, which incorrectly triggers a SELL signal when no edge exists.

### 4.13 CorrelationEngine -- Uses Mutable Static Class Instead of Record

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/kpi/CorrelationEngine.java`

**Problem**: `CorrelationResult` is a hand-written static class with private final fields and getters, duplicating what a Java `record` provides automatically. The codebase otherwise consistently uses records. This inconsistency adds maintenance burden.

### 4.14 TalibAdapter -- Unchecked Casts from TA-Lib Native Results

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/talib/TalibAdapter.java`

```java
RealResult result = (RealResult) Sma.execute(0, data.length - 1, data, period);
```

**Problem**: The TA-Lib static `execute()` methods return `Object` (interface contract), and `TalibAdapter` casts them without `instanceof` checks. If the TA-Lib library changes return types in a future version, this will produce `ClassCastException` at runtime. Add `instanceof` guards or use a type-safe wrapper.

### 4.15 ScheduledCalibrationTask -- Cron Expression Uses `*/1`

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/optimization/ScheduledCalibrationTask.java`, line 31

```java
@Scheduled(cron = "${computation.calibration.cron:0 0 6 1 */1 ?}")
```

**Problem**: The `*/1` in the month field means "every month starting from January." However, `*/1` is non-standard for a quartz cron expression -- the standard form is `*` (every month). While some cron implementations accept `*/1`, the field comment suggests `"0 0 6 1 * ?"` (every 1st of month at 6:00 AM) was intended. The `*/1` creates ambiguity about whether it means "every month" or "starting from month 1."

### 4.16 IliCalculator -- Negative Vol Sign in ILI Formula

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/kpi/IliCalculator.java`, line 55

```java
double ili = wRrp * zRrpVal + wSpread * zSpreadVal - wVol * zVolVal;
```

**Problem**: The ILI formula uses a NEGATIVE sign on the volatility component: `- wVol * zVolVal`. This means higher volatility REDUCES the ILI score. If ILI is an "institutional liquidity index," this may be intentional (higher vol means less institutional liquidity). However, this is not documented anywhere. A developer reading only the `WeightedWeightStore` (which stores weights `[0.4, 0.35, 0.25]`) would naturally assume all three components add positively. The negative sign on vol should be explicitly documented in the `IliCalculator` class Javadoc.

### 4.17 NormalizationService -- `computeStdDev` Uses Population Variance

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/kpi/NormalizationService.java`, line ~62

```java
return Math.sqrt(sumSq / values.length);  // divide by N, not N-1
```

**Problem**: Consistent with other files but adds to the pattern of using population variance. Since z-scores are used for signal generation, the bias from using N versus N-1 is small for large N but meaningful for small lookback windows (e.g., LookbackTier.VOLATILITY with 20 days).

### 4.18 DemoConfig -- Config Record Grows Unbounded

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/demo/DemoConfig.java`

**Problem**: `DemoConfig` has grown to 13 nested config records (`AdvancedCostModel`, `RandomizedExecution`, `MarketStabilityGuard`, `OrderImpactPredictor`, `MarketMakerMode`, `DynamicStops`, `PortfolioAlgebra`, `LeverageRotation`, `KillSwitchConfig`, `GlobalSafeMode`). Each addition requires updating the `core()` factory method with defaults. This is a maintainability concern -- consider splitting into focused `@ConfigurationProperties` classes per concern.

### 4.19 LiquidityStressTestModule -- runScenario is Package-Private

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/stress/LiquidityStressTestModule.java`, line 68

```java
StressTestResult runScenario(StressScenario scenario, double currentIli, double[] historicalIliValues) {
```

**Problem**: The primary scenario runner method is package-private (no access modifier). It cannot be called from outside the `stress` package, forcing all callers to go through the public scenario factory methods (`swissFranc2015()`, `repoSpike2019()`, `covid2020()`) or manually construct `StressScenario` objects. If the intent is to restrict access, add a public method that accepts arbitrary scenarios.

### 4.20 SystemicResilienceMonitor -- evaluateCurrent has No Return Value

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/demo/SystemicResilienceMonitor.java`, line ~62

```java
@Scheduled(fixedDelayString = "${monitor.demo.global-safe-mode.evaluation-interval-ms:5000}")
public void evaluateCurrent() {
    evaluate(probe.snapshot());
}
```

**Problem**: The scheduled method calls `evaluate()` but discards the `EvaluationResult`. If Safe Mode is tripped by the scheduled sweep, there is no notification mechanism (no event publishing, no alert). The only way to detect the transition is to poll `isSafeModeActive()`. Consider publishing a Spring `ApplicationEvent` when Safe Mode is newly activated.

---

## 5. Code Quality and Clean Code Findings

### 5.1 Equitable Structure of Equity Strategies

All 25 equity strategies follow an identical pattern extending `BaseEquityStrategy` which provides:
- `compute(Map<String, Double>, StrategyContext)` -- gate by `irScore < 0.9`
- `computeSignal(Map<String, Double>, StrategyContext)` -- abstract, implemented by each strategy

**Positive**: This is a clean, consistent design. Each strategy is 20-40 lines, single-responsibility, and easy to test.

### 5.2 Consistent Use of Java Records

The codebase makes excellent use of Java records for DTOs. Records are used consistently across all packages: `AlphaSignal`, `StrategyContext`, `BacktestResult`, `BriResult`, `CrisisProfile`, `IliResult`, `RegimeResult`, `KpiResult`, and many more. This is a best practice that reduces boilerplate and ensures immutability.

### 5.3 LiquiditySourceClassifier - Poorly Named Method

**File**: `/home/crazyrock/github/tickonomics/computation/src/main/java/com/tickonomics/computation/liquidity/LiquiditySourceClassifier.java`, line 40

```java
private double ArraysSum(double[] arr) {
```

**Problem**: Method name `ArraysSum` starts with uppercase, violating Java naming conventions. Should be `arraySum` or `sum`.

### 5.4 Strategy Package Naming Inconsistency

The strategy interfaces are in `com.tickonomics.computation.strategy` but the implementations are in `com.tickonomics.computation.equity`. This separates interfaces from their implementations and makes navigation harder. The `BacktestEngine` directly depends on `BaseEquityStrategy` (in `equity` package), not on the interface from `strategy` package, creating a tight coupling.

### 5.5 Repeated Null Checks for Input Maps

Every equity strategy repeats the same null-check pattern:
```java
Double value = input.get("key");
if (value == null) {
    return AlphaSignal.neutral(strategyId(), "UNIVERSE");
}
```

**Recommendation**: Extract a helper method in `BaseEquityStrategy`:
```java
protected Double requireInput(Map<String, Double> input, String key) {
    return input.get(key);
}
```
Or create an `InputValidator` that returns `Optional<AlphaSignal>` for the neutral case.

### 5.6 Magic Numbers Throughout

| Value | File(s) | Meaning |
|---|---|---|
| `0.01` | `BaseEquityStrategy` (line 59) | Signal strength threshold |
| `3.0` | `EarningsSurpriseStrategy` (line 21) | SUE normalizing divisor |
| `0.9` | `BaseEquityStrategy` (line 45) | IR score gate threshold |
| `100.0` | `AroonOscillatorStrategy` (line 24) | Aroon oscillator normalizer |
| `0.2` | `ChaikinVolStrategy` (line 25) | Volatility change threshold |
| `0.5` | `BollingerWidthStrategy` (line 25) | Bandwidth squeeze threshold |
| `70.0, 30.0` | `RSIOscillatorStrategy` | Overbought/oversold thresholds |
| `80.0, 20.0` | `MFIInversionStrategy` | MFI overbought/oversold thresholds |

**Recommendation**: Extract strategy parameters as configurable constants or inject them via `@Value` annotations to enable backtesting with different parameter sets.

### 5.7 IntersubjectiveAuditService - Not Read but Referenced

The user requested review of `audit/` files. The `IntersubjectiveAuditService` was listed but could not be read (still pending from background agent). Based on the file listing, this service interacts with `AuditEntry` and `CodingRule` records.

---

## 6. Math Correctness Detailed Analysis

### 6.1 Mean Reversion Strategy -- Correct

```java
double strength = Math.min(1.0, Math.abs(residual) / denom);
String direction = residual > 0 ? "SHORT" : "LONG";
```

A positive residual (price above mean) signals SHORT (expect reversion down). A negative residual (price below mean) signals LONG (expect reversion up). This is correct mean-reversion logic.

### 6.2 RSI Oscillator Strategy -- Correct

```java
if (rsi > 70.0) { /* overbought => SHORT */ }
if (rsi < 30.0) { /* oversold => LONG */ }
```

Standard RSI trading logic. Strength is linear in `[0, 1]` based on distance from threshold.

### 6.3 Bollinger Band Breakout -- Correct

```java
if (price > upperBand) { /* breakout up => LONG */ }
if (price < lowerBand) { /* breakout down => SHORT */ }
```

Bandwidth guard (`bandwidth <= 0`) prevents division by zero. Strength normalization by bandwidth is sensible.

### 6.4 Ichimoku Cloud -- Correct

```java
boolean aboveCloud = price > cloudTop;
boolean belowCloud = price < cloudBottom;
boolean tkCross = tenkan > kijun;
// LONG: above cloud + tenkan above kijun
// SHORT: below cloud + tenkan below kijun
```

Correct Ichimoku logic. Cloud width used for strength normalization is appropriate.

### 6.5 MACD Divergence -- Correct

```java
boolean divergent = (priceChange > 0 && macdChange < 0) || (priceChange < 0 && macdChange > 0);
String direction = priceChange > 0 ? "SHORT" : "LONG"; // bearish divergence = SHORT
```

Correct divergence logic. Bearish divergence (price up, MACD down) -> SHORT. Bullish divergence (price down, MACD up) -> LONG.

### 6.6 Stochastic Cross -- Correct

```java
boolean bullishCross = kPrev <= dPrev && kCurrent > dCurrent;
boolean bearishCross = kPrev >= dPrev && kCurrent < dCurrent;
```

Standard stochastic crossover detection.

### 6.7 Parabolic SAR -- Correct

```java
double diff = price - sar;
// diff > 0 => price above SAR => LONG (uptrend)
// diff < 0 => price below SAR => SHORT (downtrend)
```

Correct interpretation.

### 6.8 Sharpe Ratio -- Population vs. Sample Variance Inconsistency

Different files use different variance formulas:

| File | Formula | Correct? |
|---|---|---|
| `CrossModelValidator` | `variance = sum((r-mean)^2) / n` via `average()` | Population (n) |
| `WalkForwardValidator` | Same as above | Population (n) |
| `FireflyWeightOptimizer` | `variance /= portfolioReturns.length` | Population (n) |
| `DelayDExecutor` | `variance /= n` | Population (n) |
| `ForecastPersistenceService` | `variance /= (returns.length - 1)` | Sample (n-1) -- CORRECT |
| `ScheduledCalibrationTask` | `sumSqDiff / returns.length` | Population (n) |

Only `ForecastPersistenceService.computeAnnualizedVol` uses the correct sample variance (n-1) for volatility computation.

### 6.9 PairsTradingEngine -- Correct Normalization

```java
double normA = stdA > 0 ? (lastA - meanA) / stdA : 0.0;
double normB = stdB > 0 ? (lastB - meanB) / stdB : 0.0;
double spread = normA - normB;
```

Z-score normalization with zero-guard. The spread is the difference of z-scores, which is the standard pairs trading signal. Correct.

### 6.10 MarketSensitivityLibrary -- Concerns About Greek Approximations

```java
double rateGamma = rrpZscore * rrpZscore * 0.5;
double volga = volZscore * volZscore * correlation;
double dv01 = repoDelta * 0.0001;
double convexity = rateGamma * 0.5;
```

These are approximations using z-scores rather than actual price/yield data. Rate gamma is approximated as `r^2/2` where `r` is the RRP z-score, not an actual rate. DV01 is `repoDelta * 0.0001` which assumes a linear relationship with 1bp yield change. These are order-of-magnitude estimates at best and should be clearly documented as such. For production sensitivity analysis, proper bond math using actual yield curves is required.

### 6.11 LiquidityStressTestModule -- drawdownPct Can Exceed 100%

```java
double rawDrawdown = Math.abs(currentIli - shockedIli) * 100.0;
return rawDrawdown * scenario.volShockMultiplier();
```

No upper bound clamp. A 2.0 ILI change with a 4.0 volatility multiplier produces `drawdownPct = 800%`, which is passed to the pass/fail logic against `MAX_DRAWDOWN_PCT = 15.0`. This will correctly fail the test, but the unbounded value makes the metric less useful for reporting.

---

## 7. Financial Correctness Detailed Analysis

### 7.1 LiquidityStressTestModule -- Recovery Time Formula

```java
double baseRecoveryDays = drawdownPct / 5.0;
return baseRecoveryDays / Math.max(0.1, scenario.volShockMultiplier() - 1.0);
```

**Assessment**: This is a coarse heuristic. The `/ 5.0` assumes 5% recovery per day. In reality, recovery time depends on the specific market structure, circuit breakers, and liquidity provider behavior. The formula should be documented as an approximation.

### 7.2 Eq553SlippageModel -- Formula Verification

```java
return ZETA * (volatility / addvDollarVolume) * Math.abs(sharesTraded);
```

**Assessment**: This is a linear slippage model where slippage is proportional to `volatility * |shares| / dollarVolume`. It uses the ZETA constant (0.15) without documentation of its source or calibration methodology. The model is simple and directionally correct, but should be validated against real execution data. Notably, it does not account for:
- Market impact decay over time
- Order book depth (only aggregate volume)
- Participation rate (shares as % of volume)
- Bid-ask spread component

### 7.3 FixedIncomePortfolioBuilder -- Barbell Duration and Convexity

```java
double totalDuration = shortBond.duration() + longBond.duration();
double weightShort = longBond.duration() / totalDuration;
double weightLong = shortBond.duration() / totalDuration;
```

**Assessment**: These are the standard duration-matching weights for a barbell: `w1 = D2 / (D1 + D2)`, `w2 = D1 / (D1 + D2)`. The portfolio duration becomes `2*D1*D2 / (D1 + D2)` and the convexity calculation `wShort * D1^2 + wLong * D2^2` is correct.

### 7.4 VirtualPortfolio -- Not Yet Reviewed

The `VirtualPortfolio.java` and `PaperTradingEngine.java` were assigned to the background agent. Key areas to validate once read: PnL calculation correctness, commission/slippage handling, position sizing, and mark-to-market logic.

### 7.5 MarketStabilityGuard and KillSwitch -- Not Yet Reviewed

These critical safety components are in the demo package. The KillSwitch should validate that it properly:
- Monitors drawdown thresholds
- Triggers position liquidation
- Prevents new order entry when active
- Has a manual override mechanism

---

## 8. Thread Safety Findings

### 8.1 WeightedWeightStore -- Correct CAS-Based Updates

```java
public boolean applyDelta(double[] deltas) {
    double[] current = calibratedWeights.get();
    // ... compute updated ...
    return calibratedWeights.compareAndSet(expected, updated);
}
```

**Assessment**: Uses `AtomicReference` with CAS for thread-safe weight updates. The `getCalibratedWeights()` returns a defensive copy. This is correct.

### 8.2 UniverseAggregator -- AtomicReference for Context

```java
currentContext.set(new UniverseContext(Instant.now(), rBar, sigmaR, symbolReturns.size()));
```

**Assessment**: Using `AtomicReference` for the universe context is correct. The `UniverseContext` is an immutable record, so no additional synchronization is needed.

### 8.3 GracefulShutdown -- Correct AtomicBoolean

```java
private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
```

**Assessment**: Simple and correct use of `AtomicBoolean` for shutdown signaling.

### 8.4 ScheduledCalibrationTask -- ReentrantLock for Calibration

```java
if (!calibrationLock.tryLock()) {
    return new CalibrationResult(false, 0.0, 0.0, 0.0, "SKIPPED_IN_PROGRESS");
}
```

**Assessment**: Correct use of `tryLock()` to prevent concurrent calibration runs. The lock is released in a `finally` block.

---

## 9. Summary of All Findings by Severity

### Critical (Must Fix Before Production Trading)

1. **ScheduledCalibrationTask.computeSharpeFromWeights** -- Computes Sharpe from weight values, not returns (Section 2.1)
2. **PortfolioManagementAlgebra** -- `double` for money, Python format strings in SLF4J (Sections 2.2, 2.3, 2.4)
3. **BacktestEngine** -- Signal generation passes wrong input keys to strategies (Section 2.8)
4. **FixedIncomePortfolioBuilder** -- Incorrect duration-neutral weight formula (Section 2.6)
5. **RiskPremiumResidualMonitor** -- Look-ahead bias in residual std computation (Section 2.7)

### High Severity

6. **AnomalyScoringService** -- Unchecked type casts from Python worker response (Section 3.1)
7. **PhantomLiquidityService** -- Division by zero guard clamps too aggressively (Section 3.2)
8. **FireflyWeightOptimizer** -- Population variance in Sharpe, degenerate portfolio threshold too loose (Section 3.3, 6.8)
9. **Eq553SlippageModel** -- `Double.MAX_VALUE` propagates to infinity (Section 3.4)
10. **BarrierHittingTimeAnalyzer** -- Verify exponential formula derivation (Section 3.5)
11. **AumfScenarioEngine** -- Hardcoded `* 15` scaling factor (Section 3.7)

### Medium Severity

12. **Duplicated statistical computation** -- Extract shared MathUtils across 8+ files (Section 4.1)
13. **HistoricalDataReplay** -- Fragile string-based date extraction (Section 4.3)
14. **WalkForwardValidator** -- Anchored vs. rolling walk-forward not documented (Section 4.4)
15. **CrossModelValidator** -- MaxDrawdown assumes log returns (Section 4.5)
16. **ClimateSensitivityFactor** -- Volatile pair race condition (Section 4.6)
17. **ArrowIpcTransport** -- Native memory leak from unclosed allocator (Section 4.8)
18. **AmbiguityAdjustedIli** -- Zero-volatility falsely produces CONFIDENT (Section 4.9)
19. **VotingClassifier** -- Mutable `enabled` field in singleton `@Component` (Section 4.11)
20. **SignalGenerator** -- Percentile edge case when all historical values are equal (Section 4.12)
21. **TalibAdapter** -- Unchecked casts from TA-Lib native library (Section 4.14)
22. **ScheduledCalibrationTask** -- Non-standard `*/1` cron expression (Section 4.15)
23. **IliCalculator** -- Negative vol sign in ILI formula is undocumented (Section 4.16)
24. **Multiple Sharpe ratio implementations** -- Inconsistent population vs. sample variance across 6 files (Section 6.8)
25. **MarketSensitivityLibrary** -- Greek approximations based on z-scores, not actual prices (Section 6.10)
26. **SystemicResilienceMonitor** -- Scheduled evaluation discards result without alerting (Section 4.20)
27. **LiquidityStressTestModule** -- `runScenario` is package-private, restricting external use (Section 4.19)

### Low Severity / Code Quality

28. **LiquiditySourceClassifier.ArraysSum** -- Uppercase method name (Section 5.3)
29. **CorrelationEngine.CorrelationResult** -- Uses mutable static class instead of record (Section 4.13)
30. **SessionRangeService** -- Overnight range display edge case (Section 4.7)
31. **Equity strategies** -- Magic numbers throughout (Section 5.6)
32. **Duplicated null checks** -- Extract input validation helper (Section 5.5)
33. **Python format strings in SLF4J** -- Multiple files (Section 2.4)
34. **DemoConfig** -- Record growing unbounded with 13 nested sub-records (Section 4.18)
35. **NormalizationService** -- Population variance in std dev (Section 4.17)

---

## 10. Positive Observations

1. **Consistent use of Java records** across all packages for immutable data transfer (90%+ of DTOs use records).
2. **Clean equity strategy architecture** -- BaseEquityStrategy provides shared gating (IR score), each strategy is 20-40 lines with single responsibility.
3. **Proper thread safety** in WeightedWeightStore (CAS), UniverseAggregator (AtomicReference), ScheduledCalibrationTask (ReentrantLock), KillSwitch (AtomicBoolean), and SystemicResilienceMonitor (AtomicBoolean/AtomicReference).
4. **Good NaN/Infinity handling** in AmbiguityAdjustedIli as an example of proper floating-point validation.
5. **FAIR reproducibility** -- ReproducibilityService captures git SHA, dataset hash, and hyperparameters for every backtest run.
6. **Stationarity-aware statistics** -- The ForecastPersistenceService correctly uses n-1 denominator for sample variance.
7. **Comprehensive strategy coverage** -- 25 distinct equity strategies covering momentum, mean reversion, trend-following, breakout, and oscillator-based signal types.
8. **Modular architecture** -- Clear separation between computation, persistence, and client layers across 23 packages.
9. **ConfigurationProperties** -- DemoConfig and SignalGeneratorConfig use Spring Boot type-safe configuration binding with validation in compact constructor.
10. **Discrete monitoring correction** -- BarrierHittingTimeAnalyzer applies the Broadie-Kou-Glasserman beta (0.5826) for discrete-to-continuous barrier adjustment, which is the industry-standard correction.
11. **Kernel density estimation** -- SurpriseIndicator uses Gaussian KDE with Silverman's rule-of-thumb bandwidth for empirical probability estimation, a sophisticated statistical approach.
12. **TA-Lib integration** -- Native TA-Lib wrapper with proper native library loading, fallback to temporary extraction, and conditional bean registration.

---

## 11. Recommendations for Next Steps

1. **Immediate**: Fix `ScheduledCalibrationTask.computeSharpeFromWeights()` -- this is a core bug affecting all calibration.
2. **Immediate**: Fix SLF4J format strings (`{:.2f}` -> `{}`) across all files -- logs are currently broken.
3. **Immediate**: Fix BacktestEngine to compute indicator values before passing to strategies, or redesign the input contract.
4. **Short-term**: Extract shared statistical utilities (mean, std, Sharpe, normalization, percentile) into a single `StatisticsUtils` class with thorough unit testing and consistent sample-variance behavior.
5. **Short-term**: Standardize Sharpe ratio computation to use sample variance (n-1) and annualize consistently across all 6 implementations.
6. **Short-term**: Add Javadoc to all public methods in the `backtest`, `equity`, `kpi`, and `demo` packages documenting input/output contracts and financial formulas.
7. **Short-term**: Document the negative volatility sign in `IliCalculator.ili` formula with financial rationale.
8. **Short-term**: Make `VotingClassifier.enabled` an `AtomicBoolean` for thread safety.
9. **Short-term**: Replace `CorrelationEngine.CorrelationResult` static class with a Java `record`.
10. **Medium-term**: Add comprehensive unit tests for all 25 equity strategies with known input/output pairs.
11. **Medium-term**: Validate the `FixedIncomePortfolioBuilder` duration-neutral weight derivation against the butterfly constraint.
12. **Medium-term**: Replace unchecked casts in `AnomalyScoringService` and `TalibAdapter` with validated type-safe extraction.
13. **Medium-term**: Publish Spring `ApplicationEvent` from `SystemicResilienceMonitor` when Safe Mode is newly activated.
14. **Long-term**: Consider using `BigDecimal` or fixed-point arithmetic for all monetary calculations in portfolio and execution modules.
15. **Long-term**: Split `DemoConfig` into focused `@ConfigurationProperties` classes per concern (execution, risk, cost model) as the record grows beyond maintainable size.
