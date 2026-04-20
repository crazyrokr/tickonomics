# Computation Engine — 37 Missing Components

**Plan ref:** `05-computation-engine.md`

## v4 Components (Proposals 02–04)

| #   | Component                        | Plan Section     | Description                                                    |
| --- | -------------------------------- | ---------------- | -------------------------------------------------------------- |
| 1   | `LiquidityStressTestModule`      | §v4 Governance   | Swiss Franc 2015, Repo Spike 2019, COVID 2020 stress scenarios |
| 2   | `FireflyWeightOptimizer`         | §v4 Optimization | Population-based metaheuristic, A/B test vs Bayesian           |
| 3   | `RegimeAwareWeightingService`    | §v4 Optimization | Efficiency-regime weight adjustments                           |
| 4   | `AmbiguityAdjustedIli`           | §v4 ILI          | Uncertainty bands, UNCERTAIN status                            |
| 5   | `ScheduledCalibrationTask`       | §v4 Optimization | 30-day interval autonomous calibration                         |
| 6   | `SurpriseIndicator`              | §v4 ILI          | Information theory entropy/surprise scoring                    |
| 7   | `ClimateSensitivityFactor`       | §v4 Climate      | Climate-adjusted ILI thresholds                                |
| 8   | `ClimateRiskGuard`               | §v4 Climate      | Climate-adjusted ILI confidence                                |
| 9   | `SessionRangeService`            | §v4 Regime       | Session-aware regime detection (Asian/European/US)             |
| 10  | `ParticipationGovernanceService` | §v4 Governance   | Formal signal decomposition with admissibility checks          |

## v5 Core Components (Proposals 05–07)

| #   | Component                       | Plan Section  | Description                                                         |
| --- | ------------------------------- | ------------- | ------------------------------------------------------------------- |
| 11  | `ComovementTrigger`             | §v5 Liquidity | Liquidity comovement factor integration                             |
| 12  | `PhantomLiquidityService`       | §v5 Liquidity | PLI calculation and ILI discounting                                 |
| 13  | `ToxicityAdjustedIli`           | §v5 Liquidity | Toxicity-based ILI sensitivity adjustment                           |
| 14  | `AlgorithmicIntensityMetric`    | §v5 Liquidity | Message-based AT proxy with quintile bucketing                      |
| 15  | `BehaviouralRiskProcessor`      | §v5 Liquidity | BRI with 5th systemic risk axis                                     |
| 16  | `TimeOfDayThresholdManager`     | §v5 Liquidity | Adaptive intraday thresholds (tighten at open/close)                |
| 17  | `DefiningRangeService`          | §v5 Liquidity | DR-based signal confidence, news confidence decay                   |
| 18  | `LiquiditySourceClassifier`     | §v5 Liquidity | Trader type estimation (Algo/Institutional/Professional/Retail)     |
| 19  | `LiquidityMeanReversionSpeed`   | §v5 Liquidity | AT activity lagged quality effect                                   |
| 20  | `LiquidityPremiumFactor`        | §v5 Liquidity | AT intensity premium in cost-benefit model                          |
| 21  | `StrategicRunService`           | §v5 Core      | Groups consecutive buy/sell child orders for pattern identification |
| 22  | `PriceImpactKpi`                | §v5 Core      | NBBO midpoint at submission time                                    |
| 23  | `InformationEfficiencyAnalyzer` | §v5 Core      | Price Jump Ratio diagnostic                                         |

## v5 Execution & Sentiment Components (Proposals 08–09)

| #   | Component                      | Plan Section  | Description                                                  |
| --- | ------------------------------ | ------------- | ------------------------------------------------------------ |
| 24  | `LeverageSignaler`             | §v5 Execution | 200-day MA leverage rotation (LEVERAGE_ON/OFF)               |
| 25  | `PairsTradingEngine`           | §v5 Execution | Minimum-distance pairs trading verification                  |
| 26  | `ComparativeExecutionAnalysis` | §v5 Execution | Passive (limit-order) vs aggressive (market-order) execution |
| 27  | `PortfolioManagementAlgebra`   | §v5 Execution | Margin algebra, standardized cost model                      |
| 28  | `AlgorithmicBehaviorAlignment` | §v5 Execution | Time-decay for IntradayProxyService                          |
| 29  | `SaliProcessor`                | §v5 Sentiment | Sentiment-augmented ILI (70% ILI / 30% sentiment)            |
| 30  | `SentimentVolatilityGuard`     | §v5 Sentiment | BERT certainty-based regime threshold adjustment             |

## v5.1 Advanced Components (Proposals 10–12)

| #   | Component                    | Plan Section      | Description                                                      |
| --- | ---------------------------- | ----------------- | ---------------------------------------------------------------- |
| 31  | `AgnosticAggregator`         | §v5.1 ML          | Regret-minimization weight aggregation using exponential weights |
| 32  | `WalkForwardValidator`       | §v5.1 ML          | Anchored walk-forward analysis for OOS validation                |
| 33  | `CrossModelValidator`        | §v5.1 ML          | Tournament framework ILI vs XGBoost vs LSTM                      |
| 34  | `VotingClassifier`           | §v5.1 ML          | Optional ML confirmation filter                                  |
| 35  | `MarketEfficiencyMonitor`    | §v5.1 Sensitivity | Fundamental-algorithmic gap indicator                            |
| 36  | `RiskPremiumResidualMonitor` | §v5.1 Sensitivity | Q-world validation of ProxyDivergenceGuard                       |
| 37  | `MarketSensitivityLibrary`   | §v5.1 Sensitivity | Standardized Greeks nomenclature (Repo-Delta, Rate-Delta, Volga) |
