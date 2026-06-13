# Tickonomics v6: Comprehensive Architectural Diagrams

This document contains all data flow, sequence, state, and deployment diagrams for the tickonomics v6 architecture.
Diagrams are written in Mermaid syntax and rendered via mermaid.ink.

---

## 1. System Topology & Data Flow (High-Level)

End-to-end view of all data sources, processing layers, and consumer applications.

```mermaid
graph TD
    %% External Data Sources
    FRED["FRED API<br/>(EFFR, RRP, IORB, WALCL)"]
    NYFed["NY Fed API<br/>(SOFR, TGCR, BGCR, T-Bill)"]
    YahooFinance["Yahoo Finance<br/>(Equity Prices, Options)"]
    Finnhub["Finnhub<br/>(Equity WS, Forex)"]
    AlphaVantage["Alpha Vantage<br/>(Fundamentals)"]
    DataHub["DataHub<br/>(CSV Backfill: S&P, VIX, Oil, Gold)"]
    KenFrench["Ken French Data Library<br/>(Factor Returns)"]
    FedRSS["Fed RSS<br/>(Meeting Minutes)"]
    Disaster["USGS / GDACS<br/>(Disaster Alerts)"]
    Economic["Economic Calendar API"]
    News["News / Sentiment<br/>(FinBERT Input)"]

    %% Ingestion Layer
    FRED --> Ingestion
    NYFed --> Ingestion
    YahooFinance --> Ingestion
    Finnhub --> Ingestion
    AlphaVantage --> Ingestion
    DataHub --> Ingestion
    KenFrench --> Ingestion
    FedRSS --> Ingestion
    Disaster --> Ingestion
    Economic --> Ingestion
    News --> Ingestion

    subgraph "Ingestion & Resilience Layer"
        Ingestion["Ingestion Orchestrator"]
        Ingestion --> FredClient["FredClient"]
        Ingestion --> NyFedClient["NyFedClient"]
        Ingestion --> FinnhubWs["FinnhubWsClient"]
        Ingestion --> YahooFinClient["YahooFinanceClient"]
        Ingestion --> DisasterClient["DisasterAlertClient"]
        Ingestion --> EconClient["EconomicCalendarClient"]

        FredClient --> CDM["CDM Adapter Layer"]
        NyFedClient --> CDM
        FinnhubWs --> CDM
        YahooFinClient --> CDM

        CDM --> QC["DataQualityChecker<br/>+ DeRounding + Periodicity"]
        QC -->|"Async anomaly"| Sidecar["Autoencoder Sidecar"]
        Sidecar -->|"Reconstruction MSE"| QC

        QC --> Buffer["Tiered Buffer<br/>(Chronicle Queue)"]
        Buffer --> Writer["TimescaleDbWriter<br/>(Idempotent, Bulkhead-isolated)"]
    end

    Writer --> DB[("TimescaleDB<br/>(Hypertables + CAgg)")]
    DB --> Comp

    subgraph "Java Backend (Virtual Threads + Spring MVC)"
        Comp["Computation Engine<br/>(64 Components)"]

        subgraph "Core Pipeline"
            Norm["NormalizationService<br/>(Z-score, tiered lookback)"]
            ILI["AdaptiveIliCalculator<br/>(Dynamic weight redistribution)"]
            Regime["RegimeDetector<br/>(GARCH / CNN-LSTM / QED / K-means)"]
            Signal["SignalGenerator<br/>(Percentile-rank, regime-adaptive)"]
            Alert["AlertManager"]
        end

        Comp --> Norm --> ILI --> Regime --> Signal --> Alert

        subgraph "v5 Extended Modules"
            Leverage["LeverageSignaler"]
            Pairs["PairsTradingEngine"]
            SentimentProc["SaliProcessor<br/>+ SentimentVolatilityGuard"]
            ExecAnalysis["ComparativeExecutionAnalysis<br/>(Passive vs Aggressive)"]
            PortfolioAlg["PortfolioManagementAlgebra"]
            Markov["MarkovStopService"]
            Aggregator["AgnosticAggregator<br/>(Regret minimization)"]
            Efficiency["MarketEfficiencyMonitor"]
            RiskPrem["RiskPremiumResidualMonitor"]
            GreeksLib["MarketSensitivityLibrary<br/>(Greeks nomenclature)"]
        end

        ILI --> Leverage
        Signal --> Pairs
        Signal --> SentimentProc
        Signal --> ExecAnalysis
        Signal --> PortfolioAlg
        Signal --> Markov
        ILI --> Aggregator
        ILI --> Efficiency
        ILI --> RiskPrem
        ILI --> GreeksLib

        Gov["ParticipationGovernanceService"]
        Gov -->|"Suppress / Admit"| Signal
    end

    subgraph "Python Analytics Worker (FastAPI)"
        Worker["Analytics Engine"]

        subgraph "Statistical Services"
            ADF["ADF / Granger / OLS"]
            AIC["AIC Lag Selection"]
            GarchSvc["GARCH Regime"]
            DLRegime["CNN-LSTM / QED"]
            Sobol["Sobol Monte Carlo"]
        end

        subgraph "v5 Added Services"
            FinBERT["FinBERT Sentiment"]
            SALI["SALI Lexicon"]
            Diagnostic["Statistical Diagnostics<br/>(QQ-plot, ACF, Convergence)"]
            VolForecast["Volatility Forecasting"]
            Tournament["Model Tournament<br/>(ILI vs XGBoost vs LSTM)"]
            Shap["SHAP Explainability"]
            QWorld["Q-World Bond Pricer"]
            TBillGreeks["T-Bill Analytical Greeks"]
            CurveInterp["Yield Curve Interpolation<br/>(Nelson-Siegel)"]
            Cointegration["Cointegration Tests<br/>(Engle-Granger / Johansen)"]
        end
    end

    Comp <==>|Arrow IPC| Worker

    subgraph "Backtesting Framework"
        BT["BacktestEngine"]
        WF["WalkForwardValidator"]
        MC["Monte Carlo / Sobol"]
        Delay["DelayDExecutor<br/>(Delay-0 / Delay-1)"]
        Slippage["VolumeScaledSlippage"]
        CrossVal["CrossModelValidator"]
    end

    DB --> BT
    Signal --> BT
    BT --> WF
    BT --> MC
    BT --> Delay
    BT --> Slippage
    BT --> CrossVal

    subgraph "Demo / Virtual Portfolio"
        Paper["PaperTradingEngine"]
        VPort["VirtualPortfolio"]
        SQR["SignalQualityReport"]
        DualComp["Dual Portfolio Comparison<br/>(Passive vs Sniper)"]
    end

    Signal --> Paper --> VPort
    VPort --> SQR
    VPort --> DualComp

    subgraph "Frontend"
        Dashboard["Analytics Dashboard<br/>(Next.js 15 + D3 + Perspective)"]
        Landing["Landing Page<br/>(Next.js 15)"]
    end

    DB --> Dashboard
    Alert --> Dashboard
    DB --> Landing
```

---

## 2. Ingestion & Resilience Sequence Diagram

Detailed sequence showing CDM transformation, quality checks, resilience patterns, and persistence.

```mermaid
sequenceDiagram
    participant Source as External Source
    participant Client as Source Client (Fred/NyFed/Finnhub)
    participant CDM as CDM Adapter Layer
    participant QC as DataQualityChecker
    participant AE as Autoencoder Sidecar
    participant DR as DeRounding / Periodicity Filter
    participant Buffer as Chronicle Queue Buffer
    participant Writer as TimescaleDbWriter
    participant DB as TimescaleDB

    Source->>Client: Push / Poll Data
    Client->>Client: Generate UUID Idempotency Key
    Client->>CDM: Raw Response (source-specific type)
    CDM->>CDM: Map to CdmRateSnapshot / CdmTick / CdmOptionSnapshot
    CDM->>QC: CDM-typed data

    QC->>AE: Async anomaly check (Arrow IPC)
    AE-->>QC: {is_anomaly, reconstruction_mse}

    alt Anomaly Detected
        QC->>QC: Flag SUSPECT_ANOMALY
    else Clean Data
        QC->>QC: Pass through
    end

    QC->>DR: Quality-scored CDM data
    DR->>DR: Smooth round-time volume spikes
    DR->>DR: Filter algorithmic 1-second periodicity
    DR->>Buffer: Clean, scored data

    Note over Buffer: GC-free off-heap storage<br/>on dedicated NVMe volume

    Buffer->>Writer: Batch drain (bulkhead-isolated)
    Writer->>Writer: Check idempotency key
    alt Duplicate Key
        Writer-->>Buffer: Skip (idempotent)
    else New Key
        Writer->>DB: Batch INSERT
        DB-->>Writer: ACK
    end

    Note over Writer,DB: Circuit breaker protects DB.<br/>LKG cache serves stale data<br/>when breaker is OPEN.
```

---

## 3. Computation Engine: Core Analytic Pipeline

The primary data transformation pipeline from raw CDM snapshots to actionable signals.

```mermaid
graph TD
    subgraph "Data Retrieval"
        DB[("TimescaleDB")] -->|"tick_data, rate_snapshots"| Fetch["DataFetcher"]
        DB -->|"kpi_rolling_correlation<br/>(CAgg)"| Fetch
        Fetch --> CDM_In["CDM-Typed Input"]
    end

    subgraph "Normalization (Component 1)"
        CDM_In --> Norm["NormalizationService"]
        Norm -->|"Z = (x - SMA) / STDDEV"| Z1["Z_rrp<br/>(lookback: 252d)"]
        Norm --> Z2["Z_spread<br/>(lookback: 60d)"]
        Norm --> Z3["Z_vol<br/>(lookback: 20d)"]
    end

    subgraph "ILI Calculation (Component 2)"
        Z1 --> ILI["AdaptiveIliCalculator"]
        Z2 --> ILI
        Z3 --> ILI
        ILI -->|"w1*Z_rrp + w2*Z_spread - w3*Z_vol"| IliVal["ILI Value + Status"]
        ILI -->|"NaN component → redistribute"| WeightStore["WeightedWeightStore<br/>(base + calibrated)"]
    end

    subgraph "Stationarity & Correlation (Components 3-6)"
        CDM_In --> ADF["ADF Stationarity Check<br/>(Analytics Worker)"]
        CDM_In --> AIC["AIC Lag Selector<br/>(thin client → Worker)"]
        ADF --> Granger["Granger Causality Test"]
        AIC --> Granger
        CDM_In --> Correl["CorrelationEngine<br/>(CAgg + TA-Lib live)"]
    end

    subgraph "Regime Detection (Component 7)"
        IliVal --> Regime["RegimeDetector"]
        Regime -->|"PRIMARY"| GarchR["GARCH Regime"]
        Regime -->|"Alt A"| CNNLSTM["CNN-LSTM Hybrid"]
        Regime -->|"Alt B"| QED["QED Potential Well"]
        Regime -->|"Fallback"| KMeans["K-Means (internal)"]
        GarchR --> RegimeOut["Regime Classification<br/>(LOW / NORMAL / HIGH)"]
        CNNLSTM --> RegimeOut
        QED --> RegimeOut
        KMeans --> RegimeOut
    end

    subgraph "Signal Generation (Component 9)"
        IliVal --> Signal["SignalGenerator"]
        RegimeOut --> Signal
        Signal --> PctRank["Percentile-Rank Thresholds<br/>(Buy < 5%, Sell > 95%)"]
        PctRank --> Filters["Filter Chain"]
        Filters --> F1["Look-ahead Bias (1-day offset)"]
        Filters --> F2["Min Volatility (ADR > 0.15)"]
        Filters --> F3["Cooldown (4h default)"]
        Filters --> F4["Cost Threshold (2x estimated cost)"]
    end

    subgraph "Governance & Dispatch (Components 11, 24)"
        Filters --> Gov["ParticipationGovernanceService"]
        Gov --> Admit{Admissible?}
        Admit -->|"Yes"| Alert["AlertManager → ACTIONABLE"]
        Admit -->|"No"| Suppress["Suppress + Log Reason"]
    end

    Alert --> DB
    Suppress --> DB
```

---

## 4. Signal Generation & v5 Extended Modules

How signals flow through v5 enhancements: sentiment, leverage, pairs, stops, and execution analysis.

```mermaid
graph TD
    Core["SignalGenerator Output<br/>(ACTIONABLE / SUPPRESSED)"]

    subgraph "Sentiment Pipeline (Proposals 09, 10)"
        News["News Feed"] --> FinBERT["FinBERT Analyzer<br/>(Python Worker)"]
        News --> LexSali["SALI Lexicon<br/>(Python Worker)"]
        FinBERT --> SaliProc["SaliProcessor<br/>(70% ILI / 30% Sentiment)"]
        LexSali --> SaliProc
        Core --> SaliProc
        SaliProc --> SentGuard["SentimentVolatilityGuard<br/>(Adjust regime thresholds)"]
        SentGuard --> FinalSignal["Augmented Signal"]
    end

    subgraph "Leverage & Portfolio (Proposal 08)"
        SPY["SPY 200-day SMA"] --> LevSig["LeverageSignaler"]
        LevSig -->|"LEVERAGE_ON / OFF"| Portfolio["PortfolioManagementAlgebra<br/>(Rebalancing + Margin)"]
        Core --> Portfolio
    end

    subgraph "Pairs Verification (Proposal 08)"
        Universe["Equity Universe"] --> Pairs["PairsTradingEngine<br/>(Min-distance matching)"]
        Core --> CrossCheck{"ILI vs Pairs<br/>Diverge?"}
        Pairs --> CrossCheck
        CrossCheck -->|"Yes"| Dislocation["High dislocation probability"]
        CrossCheck -->|"No"| Confirm["Signal confirmed"]
    end

    subgraph "Execution & Stops (Proposals 08, 10)"
        Core --> ExecComp["ComparativeExecutionAnalysis<br/>(Passive vs Aggressive)"]
        ExecComp --> Passive["PassiveExecutionHandler<br/>(Limit @ fair price)"]
        ExecComp --> Sniper["SniperExecutionHandler<br/>(Market @ threshold)"]
        Core --> Markov["MarkovStopService<br/>(State-dependent exits)"]
        Core --> Voting["VotingClassifier<br/>(Optional ML confirmation)"]
    end

    subgraph "Risk & Sensitivity (Proposals 11, 12)"
        Core --> Efficiency["MarketEfficiencyMonitor<br/>(ILI vs AT imbalance gap)"]
        Core --> RiskPrem["RiskPremiumResidualMonitor<br/>(P-world vs Q-world)"]
        Core --> Greeks["MarketSensitivityLibrary<br/>(Repo-Delta, Rate-Gamma, Volga)"]
    end

    FinalSignal --> Demo["Demo / Virtual Portfolio"]
    Portfolio --> Demo
    Confirm --> Demo
```

---

## 5. Python Analytics Worker: Service Architecture

Internal service decomposition of the FastAPI analytics worker showing all v5 endpoints.

```mermaid
graph TD
    subgraph "Transport Layer"
        Arrow["Arrow IPC Endpoint<br/>(high-throughput time-series)"]
        REST["REST/JSON Endpoints<br/>(small payloads, config)"]
    end

    subgraph "Econometrics (v1)"
        ADF["/causality<br/>ADF + Granger + AIC"]
        OLS["/econometrics/ols<br/>Full OLS Statistics"]
    end

    subgraph "Anomaly & Drift (v4)"
        Autoenc["/anomaly/detect<br/>PyTorch Autoencoder"]
        Drift["/drift-diffusion<br/>Ito Process Simulation"]
    end

    subgraph "Regime Models (v4)"
        GarchSvc["/risk/garch-regime<br/>GARCH Volatility Regime"]
        DlSvc["/regime/hybrid<br/>CNN-LSTM Ensemble"]
        QedSvc["/regime/qed<br/>Quartic Potential Well"]
        Climate["/climate/simulate<br/>Multi-country Trade"]
    end

    subgraph "Optimization (v4)"
        SgdOpt["/risk/weight-delta<br/>Online SGD Optimizer"]
        SobolSvc["/sobol/simulate<br/>Quasi-random MC"]
        Riemann["/backtest/riemann-zeta<br/>Discrete Monitoring Correction"]
    end

    subgraph "Risk & Liquidity (v4-v5)"
        GexSvc["/risk/greeks-gex<br/>BSM Gamma + GEX"]
        LiqSvc["/liquidity/comovement<br/>PCA + Amihud"]
        ToxSvc["/liquidity/toxicity<br/>Order-to-Trade Ratio"]
    end

    subgraph "Sentiment (v5: Proposal 09)"
        FinSvc["/sentiment/analyze<br/>FinBERT"]
        SaliSvc["/sentiment/lexicon<br/>TextBlob SALI"]
    end

    subgraph "Stops (v5: Proposal 08)"
        StopSvc["/stops/calibrate<br/>Markov Stop Engine"]
    end

    subgraph "Diagnostics & Explainability (v5: Proposal 10-11)"
        DiagSvc["/diagnostics/stats<br/>QQ-plot, ACF, Convergence"]
        VolSvc["/analytics/volatility-forecast<br/>Volatility Projection"]
        TourSvc["/tournament/evaluate<br/>Model Tournament"]
        ShapSvc["/explainability/feature-importance<br/>SHAP Values"]
    end

    subgraph "Fixed Income (v5: Proposal 12)"
        QwSvc["/fixed-income/q-world-fair-value<br/>Q-World Bond Pricer"]
        GkSvc["/fixed-income/tbill-greeks<br/>T-Bill Analytical Greeks"]
        CurveSvc["/strategies/interpolate-curve<br/>Nelson-Siegel / Cubic Spline"]
    end

    Arrow --> ADF & Autoenc & GarchSvc & SgdOpt & FinSvc & DiagSvc & QwSvc
    REST --> OLS & Drift & DlSvc & QedSvc & Climate & SobolSvc & Riemann
    REST --> GexSvc & LiqSvc & ToxSvc & SaliSvc & StopSvc & VolSvc
    REST --> TourSvc & ShapSvc & GkSvc & CurveSvc
```

---

## 6. ILI Calculation Pipeline (Detailed)

Step-by-step flow of the Institutional Liquidity Index calculation with all guardrails.

```mermaid
sequenceDiagram
    participant DB as TimescaleDB
    participant Fetch as DataFetcher
    participant Norm as NormalizationService
    participant ILI as AdaptiveIliCalculator
    participant WS as WeightedWeightStore
    participant Guard as ProxyDivergenceGuard
    participant Anom as Anomaly Detection
    participant Sig as SignalGenerator
    participant Alert as AlertManager

    DB->>Fetch: Load rate_snapshots (SOFR, EFFR, RRP, IORB)
    Fetch->>Norm: CdmRateSnapshot stream

    Norm->>Norm: TA_SMA + TA_STDDEV per component
    Norm->>Norm: Tiered lookback (252d/60d/20d)

    alt Insufficient data (< 10 points)
        Norm-->>Sig: NaN + DATA_INSUFFICIENT
    else Zero variance (< 0.0001)
        Norm-->>ILI: NaN component
    else Valid
        Norm->>ILI: Z_rrp, Z_spread, Z_vol
    end

    ILI->>WS: Get effective weights (base + calibrated)
    WS-->>ILI: {w1: 0.4, w2: 0.4, w3: 0.2}

    alt Any component is NaN
        ILI->>ILI: Redistribute weight proportionally
        ILI->>ILI: Status = DEGRADED_COMPONENT_STALE
    else All valid
        ILI->>ILI: ILI = w1*Z_rrp + w2*Z_spread - w3*Z_vol
    end

    ILI->>Guard: ILI value + T-Bill proxy

    par Parallel Checks
        Guard->>Guard: 5-day correlation check
    and
        Guard->>Anom: Autoencoder anomaly check
        Anom-->>Guard: {is_anomaly, mse}
    end

    alt Divergence Detected
        Guard-->>Sig: DISLOCATED status
        Sig->>Alert: Suppress all proxy-derived signals
    else Proxy OK
        Guard-->>Sig: VALID status
    end

    Sig->>Sig: Percentile-rank over 252d window
    Sig->>Sig: Apply regime-adaptive thresholds
    Sig->>Sig: Filter chain (look-ahead, cooldown, cost)

    Sig->>Alert: Actionable signal (or suppression reason)
    Alert->>DB: Persist to signal_log + ili_history
```

---

## 7. Backtesting Framework Flow

Data flow through the backtesting engine including walk-forward validation and multi-model tournament.

```mermaid
graph TD
    subgraph "Data Preparation"
        DB[("TimescaleDB")] --> Replay["HistoricalDataReplayer<br/>(Rate snaps, OHLCV, ILI, proxy events)"]
        Replay --> Align["Time-Bucket Alignment<br/>(time_bucket + date range)"]
    end

    subgraph "Backtest Engine"
        Align --> Engine["BacktestEngine<br/>(Day-by-day replay)"]
        Engine --> Pipeline["Signal Pipeline<br/>(ILI → Regime → Signal)"]
        Pipeline --> Portfolio["Simulated Portfolio<br/>(positions, P&L, trades)"]
    end

    subgraph "Execution Realism (v5)"
        Portfolio --> Delay["DelayDExecutor<br/>(Delay-1 default)"]
        Delay --> Slippage["VolumeScaledSlippage<br/>ζ × (σ / ADDV) × |Pos|"]
        Slippage --> Cost["Transaction Cost Model<br/>(slippage + commission)"]
    end

    subgraph "Optimization"
        Cost --> Results["BacktestResult<br/>(Sharpe, Drawdown, Win Rate, Cents/Share)"]
        Results --> WeightOpt["WeightOptimizer<br/>(Bayesian / Firefly)"]
        WeightOpt --> OOS["WalkForwardValidator<br/>(Anchored OOS)"]
        OOS -->|"Improvement > 0.1"| Approval["Manual Approval Gate"]
        OOS -->|"No improvement"| Keep["Keep Current Weights"]
        Approval --> WS["WeightedWeightStore"]
    end

    subgraph "Validation & Tournament (v5)"
        Results --> CrossVal["CrossModelValidator<br/>(ILI vs XGBoost vs LSTM)"]
        CrossVal --> Tourney["Model Tournament<br/>(Regime-segmented)"]
        Tourney --> Shap["SHAP Explainability<br/>(Feature importance)"]
        Shap --> Report["Tournament Report<br/>(Per-regime comparison)"]
    end

    subgraph "Stress Testing (v4)"
        Engine --> Stress["Liquidity Stress Scenarios"]
        Stress --> S1["Swiss Franc 2015"]
        Stress --> S2["Repo Spike 2019"]
        Stress --> S3["COVID Freeze 2020"]
        Stress --> Barrier["Barrier Hitting-Time<br/>(Drift-Diffusion)"]
        Stress --> Climate["Climate Stress Scenarios"]
    end
```

---

## 8. Demo / Virtual Portfolio Flow

End-to-end signal-to-virtual-trade pipeline with dual portfolio comparison.

```mermaid
sequenceDiagram
    participant Sig as SignalGenerator
    participant Gov as ParticipationGovernance
    participant Paper as PaperTradingEngine
    participant Lev as LeverageSignaler
    participant Markov as MarkovStopService
    participant Exec as Execution Handler
    participant Port as VirtualPortfolio
    participant Report as SignalQualityReport

    Sig->>Gov: Raw signal
    Gov->>Gov: Admissibility check (ILI status, regime, proxy)

    alt Signal Inadmissible
        Gov-->>Report: Log suppression reason
    else Signal Admissible
        Gov->>Paper: ACTIONABLE signal

        Paper->>Lev: Check leverage state
        Leverage-->>Paper: LEVERAGE_ON / LEVERAGE_OFF

        alt LEVERAGE_OFF
            Paper->>Port: Reduce position size (T-bill rotation)
        else LEVERAGE_ON
            Paper->>Exec: Route to execution handler
        end

        Exec->>Exec: Select mode (Passive / Sniper)
        Exec->>Port: Execute virtual trade

        Port->>Markov: Register position for stop monitoring

        loop Position Open
            Markov->>Markov: State-dependent stop evaluation
            alt Stop triggered
                Markov->>Port: Close position
            else Opposing signal
                Sig->>Paper: Opposing signal
                Paper->>Port: Close + Reverse
            end
        end

        Port->>Report: Trade record (entry, exit, P&L)
    end

    Note over Report: Daily automated report:<br/>Hit rate, False positive rate,<br/>Degraded vs valid breakdown,<br/>Proxy divergence impact,<br/>Signal-based vs buy-and-hold SPY
```

---

## 9. QED Potential Well & State Dynamics (Proposal 03)

State-transition dynamics of the QED (Quantum Economics Dynamics) model.

```mermaid
stateDiagram-v2
    [*] --> STABLE
    STABLE --> METASTABLE: Capital Flow Proxy Divergence
    METASTABLE --> STABLE: Drift Correction
    METASTABLE --> UNSTABLE: Barrier Crossing (Crash Prob > 0.5)
    UNSTABLE --> METASTABLE: Market Stabilization
    UNSTABLE --> EXOGENOUS_SHOCK: Disaster Alert (USGS/GDACS)
    STABLE --> EXOGENOUS_SHOCK: Disaster Alert

    state EXOGENOUS_SHOCK {
        [*] --> Suppress
        Suppress --> Await: TTL Expiry or Manual Clear
    }

    EXOGENOUS_SHOCK --> STABLE: Resolved
    EXOGENOUS_SHOCK --> UNSTABLE: Shock Escalates

    note right of METASTABLE
        Potential Well:
        V(x) = ax^4 + bx^2
        Capital flow proxies:
        RRP Drain Velocity + TGA Change
    end note
```

---

## 10. AUMF Uncertainty Management Lifecycle (Proposal 06)

Five-stage uncertainty management framework controlling signal admissibility during crisis periods.

```mermaid
graph TD
    Monitor["Market Condition Stream<br/>(ILI + Volatility + GEX + AT Intensity)"] --> Engine["AUMF Scenario Engine"]

    Engine --> Profile{"Matches Crisis Profile?<br/>(COVID, Swiss Franc, Repo Spike)"}
    Profile -->|"Yes"| S3["Stage 3: SUSPENDED_UNCERTAINTY"]
    Profile -->|"No"| Diverge{"Elevated Uncertainty?<br/>(Ambiguity band exceeded)"}
    Diverge -->|"Yes"| S2["Stage 2: PROCEED_CAUTIOUSLY"]
    Diverge -->|"No"| S1["Stage 1: NORMAL"]

    S3 --> KillSwitch{"Kill-Switch<br/>Activated?"}
    KillSwitch -->|"Yes"| S4["Stage 4: SAFE_MODE<br/>(Throttle writer, stop dispatch)"]
    KillSwitch -->|"No"| S3

    S4 --> S5["Stage 5: Manual Oversight<br/>(Await operator ACK)"]
    S5 --> S2

    subgraph "Signal Admissibility Matrix"
        S1 -->|"Full dispatch"| Dispatch["ACTIONABLE signals"]
        S2 -->|"Reduced sizing"| Sized["Position size × uncertainty_factor"]
        S3 -->|"All suppressed"| Suppressed["No automated signals"]
        S4 -->|"System paused"| Paused["Writer throttled, signals stopped"]
        S5 -->|"Awaiting human"| Await["Manual review queue"]
    end
```

---

## 11. Stochastic Weight Optimization & Calibration Loop

Closed-loop optimization cycle with Bayesian/Firefly meta-heuristics and OOS validation.

```mermaid
sequenceDiagram
    participant DB as TimescaleDB
    participant BT as BacktestEngine
    participant Optimizer as Bayesian/Firefly Optimizer
    participant Sobol as Sobol Simulation
    participant OOS as WalkForwardValidator
    participant Riemann as Riemann Zeta Correction
    participant Gate as Manual Approval Gate
    participant Store as WeightedWeightStore
    participant ILI as AdaptiveIliCalculator

    loop Monthly Calibration Cycle
        DB->>BT: Load historical data (180d lookback)
        BT->>Optimizer: Fitness landscape (Sharpe per weight set)

        par Parallel Exploration
            Optimizer->>Optimizer: Bayesian GP surrogate model
        and
            Optimizer->>Sobol: Quasi-random parameter sampling
        end

        Optimizer->>Riemann: Apply discrete monitoring correction (beta=0.5826)
        Riemann-->>Optimizer: Corrected threshold estimates

        Optimizer->>OOS: Proposed weight set W_opt
        OOS->>OOS: Anchored walk-forward (window N → test N+1)
        OOS-->>BT: OOS Sharpe + Regret metric

        alt Improvement > 0.1 Sharpe (OOS)
            BT->>Gate: W_opt with evidence
            Gate->>Store: Manually approve
            Store->>Store: Update calibrated weights
            Store->>ILI: New effective weights
        else No meaningful improvement
            Note over BT: Keep current weights
        end
    end
```

---

## 12. Deployment & Container Architecture

Docker Compose topology showing all services, volumes, and inter-container communication.

```mermaid
graph TD
    subgraph "Docker Compose Environment"
        subgraph "Application Services"
            Backend["Backend (Java 25)<br/>Spring MVC + Virtual Threads<br/>Port: 8080"]
            Worker["Analytics Worker (Python 3.12)<br/>FastAPI + Uvicorn<br/>Port: 8001"]
            Dashboard["Dashboard (Next.js 15)<br/>Nginx<br/>Port: 3000"]
            Landing["Landing Page (Next.js 15)<br/>Nginx<br/>Port: 3001"]
        end

        subgraph "Data Infrastructure"
            TSDB[("TimescaleDB<br/>PG16 + Timescale Extension<br/>Port: 5432")]
            Overflow[("Chronicle Queue<br/>Overflow Volume<br/>(NVMe / tmpfs)")]
        end

        subgraph "Observability (v4)"
            Jaeger["Jaeger<br/>Port: 16686 (UI)<br/>Port: 4317 (OTLP gRPC)"]
        end
    end

    Backend -->|"JDBC"| TSDB
    Backend <==>|Arrow IPC<br/>(Socket)"| Worker
    Backend -->|"Chronicle Queue"| Overflow
    Dashboard -->|"REST + WS"| Backend
    Backend -->|"OTLP"| Jaeger
    Worker -->|"OTLP"| Jaeger

    subgraph "External Free Data Sources"
        FRED["FRED API"]
        NYFed["NY Fed API"]
        YahooFinance["Yahoo Finance"]
        Finnhub["Finnhub"]
        AlphaVantage["Alpha Vantage"]
        DataHub["DataHub (CSV)"]
        KenFrench["Ken French"]
        FedRSS["Fed RSS"]
    end

    Backend -->|"HTTPS"| FRED
    Backend -->|"HTTPS"| NYFed
    Backend -->|"HTTPS REST"| YahooFinance
    Backend -->|"WebSocket + REST"| Finnhub
    Backend -->|"HTTPS REST"| AlphaVantage
    Backend -->|"HTTPS CSV"| DataHub
    Backend -->|"HTTPS CSV"| KenFrench
    Backend -->|"RSS"| FedRSS
```

---

## 13. Dashboard Component Architecture

Frontend component hierarchy showing data sources and WebSocket connections.

```mermaid
graph TD
    subgraph "Data Layer"
        WS1["/ws/signals<br/>(Real-time signals)"]
        WS2["/ws/prices<br/>(Live price updates)"]
        API["REST API<br/>/api/v1/*"]
    end

    subgraph "Core Charts"
        PriceILI["Multi-Pane Chart<br/>Price + ILI + Signal Markers<br/>(Lightweight Charts)"]
        CorrMatrix["Correlation Matrix<br/>(Perspective Heatmap)"]
        SignalLog["Signal Log Grid<br/>(Perspective Data Grid)"]
    end

    subgraph "ILI & KPI Cards"
        ILICard["ILI Card<br/>(value, status, weights)"]
        Freshness["Data Freshness Panel<br/>(FRED, NY Fed, Proxy)"]
        KPIGrid["KPI Dashboard<br/>(LSI, Beta, RRP Velocity, GEX)"]
    end

    subgraph "Risk & Regime Panels"
        RegimePanel["Regime Indicator<br/>(GARCH / CNN-LSTM / QED)"]
        AUMFPanel["AUMF Status<br/>(5-stage uncertainty)"]
        SurprisePanel["Surprise Indicator<br/>(Entropy-based anomaly)"]
    end

    subgraph "v5 Dashboard Extensions"
        SentHeatmap["Sentiment Heatmap<br/>(FinBERT + SALI)"]
        ExecPanel["Execution Comparison<br/>(Passive vs Aggressive)"]
        PairsPanel["Pairs Verification<br/>(Divergence monitor)"]
        LevPanel["Leverage Regime<br/>(ON / OFF indicator)"]
        StopsPanel["Dynamic Stops<br/>(Markov state tracker)"]
        QQPanel["Tail Risk QQ-Plot<br/>(Distribution diagnostics)"]
        ACFPanel["Volatility ACF<br/>(Persistence chart)"]
        ConvPanel["Sample Adequacy<br/>(Convergence monitor)"]
        EffPanel["Market Efficiency Gap<br/>(ILI vs AT imbalance)"]
        MpsPanel["Monetary Policy<br/>Sensitivity"]
        TourPanel["Model Tournament<br/>(ILI vs ML comparison)"]
        GreeksPanel["Greeks Sensitivity<br/>(Repo-Delta, Rate-Gamma, Volga)"]
    end

    subgraph "Backtest & Demo"
        BTEquity["Backtest Equity Curve"]
        BTDrawdown["Drawdown Chart"]
        BTSigTimeline["Signal Timeline"]
        BTCompare["Parameter Comparison"]
        DemoPort["Demo Portfolio<br/>(P&L, trades, win rate)"]
        SignalReport["Signal Quality Report"]
    end

    WS1 --> PriceILI & SignalLog & SentHeatmap & RegimePanel & AUMFPanel
    WS2 --> PriceILI & DemoPort
    API --> ILICard & Freshness & KPIGrid & CorrMatrix
    API --> ExecPanel & PairsPanel & LevPanel & StopsPanel
    API --> QQPanel & ACFPanel & ConvPanel & EffPanel & MpsPanel
    API --> TourPanel & GreeksPanel
    API --> BTEquity & BTDrawdown & BTSigTimeline & BTCompare
    API --> SignalReport
```

---

## v6 Changelog

- **Title:** Updated from "v5" to "v6" to reflect current architecture version.
- **Section 1 (System Topology):** Replaced single `Polygon.io` data source node with separate nodes: `Yahoo Finance`, `Finnhub`, `Alpha Vantage`, `DataHub`, `Ken French`, `Fed RSS`. Removed `OpenBB` node. Updated ingestion clients accordingly (added `YahooFinanceClient`, renamed `PolygonWs` to `FinnhubWs`).
- **Section 2 (Ingestion Sequence):** Updated participant name from `Fred/NyFed/Polygon` to `Fred/NyFed/Finnhub` reflecting free data source migration.
- **Section 12 (Deployment):** Removed `OpenBB Platform` sidecar subgraph. Replaced external `Polygon.io` with separate `Yahoo Finance`, `Finnhub`, `Alpha Vantage`, `DataHub`, `Ken French`, `Fed RSS` nodes. Updated connection labels from "REST (equity)" and "WebSocket + REST" to appropriate free data source protocols.
- **All data flow descriptions:** References to "Polygon" updated to "Finnhub" where applicable. References to "OpenBB sidecar" updated to "Free data source clients".
