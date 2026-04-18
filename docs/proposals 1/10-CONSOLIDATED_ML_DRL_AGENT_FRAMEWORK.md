# Consolidated Proposal: ML/DRL Agent Framework

**Status:** Proposal
**Applicability:** Tickonomics v3 (Java 25 Spring Boot + Python FastAPI + TimescaleDB)
**Consolidation Date:** 2026-05-21

## v3 Integration Points

| Track                             | Components Affected                                                                                                                                                                                    |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Track 1 (API Contracts)           | New endpoints for DRL inference, tournament evaluation, model explainability                                                                                                                           |
| Track 3 (Analytics Worker)        | `DeepReinforcementLearningService`, `rl_service.py`, `xgboost_service.py`, `lstm_service.py`, `VolatilityForecaster`, `SignalConfirmationModel`, `ModelExplanabilityService`, LangGraph agent services |
| Track 4 (Ingestion)               | FinRL-Meta unified data processor, `FredClient`/`NyFedClient` refactoring, automated feature scaling                                                                                                   |
| Track 5 (Computation)             | `SignalGenerator`, `ExecutionOptimizer`, `AgnosticAggregator`, `WeightOptimizer`, `CrossModelValidator`, voting classifier                                                                             |
| Track 7 (Dashboard)               | DRL Attribution Panel, Signal Attribution component, Simulation-to-Reality Gauge, model comparison charts                                                                                              |
| Track 8 (Backtesting)             | `HistoricalDataReplay`, `CrossModelValidator`, walk-forward validation, multi-model performance charts                                                                                                 |
| Track 10 (Demo/Virtual Portfolio) | `PaperTradingEngine` (adaptive mode), `RlPortfolioManager`, `TwoLayerExecutionEngine`, `SpeculativeOrderTimer`, FinRL Environment Simulation                                                           |

---

## PART I: CORE PROPOSAL (PRIMARY)

The primary ML/DRL approach for Tickonomics v3.

---

### 1.1 FinRL Adaptive Trading Agent

**Source:** SSRN-3737257 (Liu et al., 2020)
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation), Track 10 (Demo/Virtual Portfolio)

#### Rationale

FinRL is an open-source library for automated stock trading using Deep Reinforcement Learning. Its layered
architecture (Environment, Agent, Application) allows training agents using state-of-the-art algorithms like PPO, DDPG,
and SAC while accounting for market frictions. The PPO agent replaces fixed percentile thresholds (5%/95%) with a
continuous value between -1 (Strong Sell) and 1 (Strong Buy), enabling more nuanced position sizing.

#### Module: DeepReinforcementLearningService (Track 3)

- **Location:** `analytics/app/services/drl/deep_rl_service.py`
- **Dependencies:** `finrl`, `gym`, `stable-baselines3`
- **Agent:** Pre-trained PPO (Proximal Policy Optimization) agent.
- **State Space:**
    - ILI current value and Z-scores.
    - Repo/Equity Beta.
    - Volatility Regime status.
    - Recent price returns (stationary data).
    - Current portfolio exposure.
- **Action Space:** Continuous value between -1 (Strong Sell) and 1 (Strong Buy).
- **Endpoint:** `/api/v1/analytics/drl-signal`

#### Module: Training Pipeline (Track 3)

- **Location:** `analytics/app/services/drl/training.py`
- **Implementation:** Training script using historical TimescaleDB data to train DRL agent on the "Liquidity Pivot"
  strategy.
- **Reward Function:** Maximize Sharpe ratio with risk penalties.

#### Module: FinRL Environment Simulation (Track 10)

- **Location:** `demo/src/main/java/com/tickonomics/demo/environment/FinRLEnvironment.java`
- **Implementation:** Use the FinRL "Environment" module to wrap the Virtual Portfolio.
    - Standardized reward functions (e.g., maximizing Sharpe ratio).
    - Evaluate effectiveness of ILI signals.
- **Verification Benchmarking:** Use standard FinRL performance metrics (annualized return, max drawdown, Sharpe) to
  automate "Verification Criteria" checks in Track 10.

#### Module: Java Backend Integration (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/drl/DrlSignalClient.java`
- **Implementation:** Java backend calls `/api/v1/analytics/drl-signal` for high-confidence confirmation.

#### Validation Criteria

- [ ] DRL agent achieves higher risk-adjusted return (Sharpe) than baseline percentile-based `SignalGenerator` in
  backtests.
- [ ] Agent correctly learns to halt trading during `DISLOCATED` proxy events without explicit hard-coding.

---

## PART II: ALTERNATIVE PLUGGABLE IMPLEMENTATIONS

Each alternative provides a different approach to the ML/DRL trading problem. They are designed as pluggable modules
that can replace or supplement the primary FinRL PPO agent.

---

### 2.1 Alternative A: DQN/PPO Adaptive Portfolio

**Source:** SSRN-6673841 (Moussa)
**Tracks:** Track 5 (Computation), Track 10 (Demo/Virtual Portfolio)
**Type:** ALTERNATIVE PLUGGABLE IMPLEMENTATION A

#### Rationale

RL agents can adapt more quickly to non-linear and volatile markets than deterministic rule sets, making them ideal for
managing virtual portfolios. This approach focuses on portfolio-level decisions rather than individual trade signals.

#### Module: RL Signal Processor (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/rl/RlSignalProcessor.java`
- **Agent:** DQN or PPO.
- **Inputs:** Current ILI state and portfolio performance.
- **Outputs:** Dynamic position-sizing and trade-timing decisions (instead of hard-coded rules).
- **Integration:** `Stable-Baselines3` via Java/Python analytics worker bridge.

#### Module: RlPortfolioManager (Track 10)

- **Location:** `demo/src/main/java/com/tickonomics/demo/portfolio/RlPortfolioManager.java`
- **Implementation:** Train RL agent on historical data in the Demo environment.
- **Benchmark:** Performance against current deterministic rule-based portfolio.

#### Validation Criteria

- [ ] Agent learns optimal behavior for volatile regimes without manual threshold updates.
- [ ] Higher P&L and lower drawdown compared to deterministic rules.

---

### 2.2 Alternative B: TD3 Continuous Action Space Execution

**Source:** SSRN-4276310
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation), Track 8 (Backtesting), Track 10 (Demo/Virtual Portfolio)
**Type:** ALTERNATIVE PLUGGABLE IMPLEMENTATION B

#### Rationale

The Twin-Delayed DDPG (TD3) algorithm in a continuous action space outperforms discrete models like DQN. It enables
dynamic position sizing (e.g., allocating 23% of capital instead of a fixed 5%) and optimizes logarithmic return rewards
using an Actor-Critic architecture.

#### Module: rl_service.py (Track 3)

- **Location:** `analytics/app/services/drl/rl_service.py`
- **Dependencies:** `PyTorch`, `Stable Baselines3` (or custom TD3 implementation).
- **Agent:** TD3 (Twin-Delayed DDPG).
- **Architecture:** Actor-Critic separation (policy vs. value estimation).
- **Reward Function:** Logarithmic return reward as described in the paper.

#### Module: Hybrid Signal Generator (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/signal/HybridSignalGenerator.java`
- **Implementation:** Combine rule-based ILI signals (macro context) with DRL agent (execution context).
    - ILI provides the "regime".
    - DRL agent determines the "intensity" of the trade.
- Replace `fixed_pct` position sizing with DRL-derived `allocation_pct`.

#### Module: ExecutionOptimizer Client (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/drl/ExecutionOptimizer.java`
- **Implementation:** Update or create a new client in Java to call the Python DRL service.
- **State Space Definition:**
    - Current ILI value and percentile.
    - Volatility Regime status.
    - Recent price returns (stationary data).
    - Current portfolio exposure.

#### Module: Agent Training with Historical Replay (Track 8)

- Use `HistoricalDataReplay` to train TD3 agent.
- `ili_history` and `zscore_series` serve as the "State Space" for the RL environment.
- Compare "ILI + Fixed Size" strategy against "ILI + DRL Optimized Size" strategy.

#### Module: Adaptive Paper Trading (Track 10)

- `PaperTradingEngine` runs the DRL agent in "inference mode" to showcase sophisticated capital management.

#### Validation Criteria

- [ ] DRL-derived allocation outperforms fixed-percent sizing on risk-adjusted returns.
- [ ] Sharpe ratio improvement is statistically significant in walk-forward testing.

---

### 2.3 Alternative C: Contextual Bandit Execution

**Source:** SSRN-4484004 (Cartea et al.)
**Tracks:** Track 10 (Demo/Virtual Portfolio)
**Type:** ALTERNATIVE PLUGGABLE IMPLEMENTATION C

#### Rationale

A two-layer execution model (strategic + speculative) using multi-task Gaussian Process bandits (MTGP-LR) can learn
reward functions to optimize child order timing in non-stationary market environments.

#### Module: TwoLayerExecutionEngine (Track 10)

- **Location:** `demo/src/main/java/com/tickonomics/demo/execution/TwoLayerExecutionEngine.java`
- **Architecture:**
    - **Strategic Layer:** ILI-based signal trigger (unchanged).
    - **Speculative Layer:** Simplified MTGP-LR contextual bandit that observes order book features to decide exact
      execution timing of orders generated by the strategic layer.

#### Module: SpeculativeOrderTimer (Track 10)

- **Location:** `demo/src/main/java/com/tickonomics/demo/execution/SpeculativeOrderTimer.java`
- **Implementation:** Utilizes market features to optimize execution timing.

#### Validation Criteria

- [ ] Reduced slippage compared to blunt market order execution.
- [ ] Bandit learns optimal timing within a reasonable number of episodes.

---

### 2.4 Alternative D: LangGraph ReAct Agent Architecture

**Source:** "Anatomy of an AI-Trader" research
**Tracks:** Track 5 (Computation), Track 8 (Backtesting)
**Type:** ALTERNATIVE PLUGGABLE IMPLEMENTATION D

#### Rationale

The ReAct (Reasoning + Acting) loop using LangGraph enables multi-step reasoning cycles that can handle unstructured
information (market news sentiment) alongside structured time-series data, leading to more "reasoned" signals rather
than purely formulaic ones. Research shows ensembles generally outperform single models by neutralizing individual model
biases.

#### Module: LangGraph ReAct Implementation (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/agent/ReActAgent.java` (orchestration) +
  `analytics/app/services/agent/langgraph_service.py` (execution)
- **Implementation:** Transition from rigid KPI calculation flow to a stateful agent graph.
- **ReAct Loop:**
    1. **Reason:** Analyze market data/news.
    2. **Act:** Execute a `get_price()` or `get_news()` tool.
    3. **Reason:** Incorporate tool output into the final decision.

#### Module: Ensemble of Specialist Agents (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/agent/EnsembleSupervisor.java`
- **Architecture:** Three distinct agent "specialists":
    1. **Macro Specialist:** Focuses on liquidity indices and macro events.
    2. **Sentiment Specialist:** Focuses on news and social media sentiment.
    3. **Technicals Specialist:** Focuses on TA-Lib based price trends.
- **Supervisor Agent:** Weights output of the three specialists to issue a final trade command.

#### Module: Dynamic Tool Definitions (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/agent/ToolDefinitionService.java`
- **Implementation:** Jinja2-based tool descriptions that are context-aware based on current market regime.
- **Example:** If volatility is high, `get_price()` automatically adds "prioritize recent intraday volatility" to its
  prompt instructions.

#### Module: Pre-Loaded Context Injection (Track 5)

- Inject current "LastQuote" data directly into the system prompt for every agent run.
- Reduces latency and token usage by ensuring the agent is always aware of the latest price state.

#### Validation Criteria

- [ ] ReAct agent produces reasoned signals that incorporate both structured and unstructured data.
- [ ] Ensemble of specialists outperforms any single specialist model.
- [ ] Dynamic tool descriptions improve signal quality in high-volatility regimes.

---

### 2.5 Alternative E: Online Learning (Regret Minimization)

**Source:** SSRN-5242085
**Tracks:** Track 5 (Computation), Track 8 (Backtesting)
**Type:** ALTERNATIVE PLUGGABLE IMPLEMENTATION E

#### Rationale

The "AGNOSTIC" framework uses online learning and online convex optimization (OCO) to build aggregators of regret
minimization algorithms. This directly addresses non-ergodic financial data that fails to generalize. Instead of static
ILI weights, an agnostic aggregator continuously updates its strategy based on real-time regret, creating a "
self-optimizing" weight system.

#### Module: AgnosticAggregator (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/weight/AgnosticAggregator.java`
- **Implementation:** Aggregate multiple weight-sets (e.g., "Macro-heavy", "Flow-heavy") using a regret-minimization
  algorithm (e.g., Exponential Weights).
- Replace static ILI weights with a dynamically-weighted combination.

#### Module: Online Convex Optimization (Track 5)

- Adaptively fine-tune strategy parameters within the `ComputationEngine` as market conditions shift.
- Reduce reliance on manual quarterly recalibration.

#### Module: Walk-Forward Validation (Track 8)

- **Location:** `backtesting/.../WalkForwardValidator.java`
- **Implementation:** Integrate walk-forward validation into the `BacktestingFramework`.
- Ensure all strategy performance metrics are validated against out-of-sample segments.
- Update `WeightOptimizer` to evaluate performance based on regret rather than just Sharpe.

#### Validation Criteria

- [ ] Aggregator weight adaptation is stable and does not exhibit erratic shifts.
- [ ] Walk-forward validation shows out-of-sample performance is maintained.
- [ ] Performance based on regret matches or exceeds Sharpe-based optimization.

---

## PART III: COMPLEMENTARY ADDITIONS

These modules provide supporting ML infrastructure that enhances any of the above agent approaches.

---

### 3.1 ML Price Prediction

**Source:** SSRN-2305886 (Kishore, 2013)
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation)
**Type:** COMPLEMENTARY

#### Rationale

Adding a predictive ML layer to the Analytics Worker validates LSI-based signals against a predictive model that
identifies short-term price drift, significantly improving signal quality.

#### Module: MLFeatureEngine (Track 3)

- **Location:** `analytics/app/services/ml/ml_feature_engine.py`
- **Feature Calculators:**
    - **Cross-Sectional:** Bid-ask pressure, order imbalance at top 5 levels.
    - **Time-Series:** Trade imbalance, EMA of weighted mid, number of price increments.

#### Module: MidPricePredictor (Track 3/5)

- **Location:** `analytics/app/services/ml/mid_price_predictor.py`
- **Dependencies:** `scikit-learn`
- **Model:** Ensemble (Random Forest) to predict mid-price direction (up/down/neutral) in the next $M$ seconds.
- **Integration:** Java backend (Track 5) polls this module to filter "Low-Confidence" signals from `SignalGenerator`.
- **Signal Confidence Filter:** Only execute signals with > 55% prediction confidence.

#### Validation Criteria

- [ ] `MLFeatureEngine` extracts features from 1-second interval treasury data.
- [ ] `MidPricePredictor` trained and evaluated using cross-validation.
- [ ] Prediction incorporated as "Signal Confidence Filter" in `SignalGenerator`.

---

### 3.2 ML Quant Finance Trends

**Source:** SSRN-3397005 (Emerson et al., 2019)
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation), Track 8 (Backtesting)
**Type:** COMPLEMENTARY

#### Rationale

LSTMs and RNNs are effective for time-series forecasting. Random Forests are effective for predicting stock price
direction. PCA/Autoencoders reduce dimensionality of systemic risk data.

#### Module: VolatilityForecaster (Track 3)

- **Location:** `analytics/app/services/ml/volatility_forecaster.py`
- **Dependencies:** `tensorflow`/`pytorch`
- **Implementation:** BiLSTM for short-term volatility regime prediction, supplementing current `TA_BBANDS` approach.
- **Endpoint:** `/api/v1/analytics/volatility-forecast`

#### Module: SignalConfirmationModel (Track 3)

- **Location:** `analytics/app/services/ml/signal_confirmation.py`
- **Dependencies:** `scikit-learn`
- **Implementation:** Random Forest trained on RSI, MACD, and other indicators to confirm ILI-generated signals.

#### Module: Non-Linear Feature Extraction (Track 5)

- **Implementation:** Use PCA or Autoencoders to reduce dimensionality of systemic risk heatmap data before it reaches
  the `RegimeDetector`.

#### Module: Cross-Validation Standards (Track 8)

- **Implementation:** Anchored walk-forward analysis to ensure Bayesian weight optimization does not overfit to
  historical noise.
- Update `WeightOptimizer` to use walk-forward validation.

#### Validation Criteria

- [ ] LSTM volatility forecast error (MAE) is lower than historical moving average baseline.
- [ ] Random Forest confirmation increases hit rate of `ACTIONABLE` signals by > 2%.

---

### 3.3 Multi-Model Benchmark

**Source:** SSRN-4901967 (2024 Update of STGP-SATA research)
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation), Track 8 (Backtesting)
**Type:** COMPLEMENTARY

#### Rationale

Establishes a high-rigor benchmarking suite comparing ILI-based rule engine against state-of-the-art ML models. Key
insight: Genetic models are more robust in "downtrend" markets, while LSTMs excel in high-data-density environments.

#### Module: Benchmark Models (Track 3)

- **Location:** `analytics/app/services/benchmark/xgboost_service.py`,
  `analytics/app/services/benchmark/lstm_service.py`
- **Dependencies:** `xgboost`, `keras`/`tensorflow`
- **Implementation:** XGBoost and LSTM models trained on the same data used for ILI (RRP, Spread, Vol).

#### Module: Automated Tournament (Track 3)

- **Endpoint:** `/tournament/evaluate`
- **Implementation:** Run current ILI signal alongside XGBoost and LSTM predictions for the same timestamp.

#### Module: CrossModelValidator (Track 8)

- **Location:** `backtesting/.../CrossModelValidator.java`
- **Implementation:** Replay historical data through all four benchmark models (MLP, SVM, XGBoost, LSTM).
- **Regime-Specific Benchmarking:** Group markets (Uptrend, Sideways, Downtrend) to identify which model class performs
  best in each regime.
- **Backtest Report:** Side-by-side performance charts for Standard ILI, XGBoost-Augmented ILI, and LSTM-Augmented ILI.

#### Module: Voting Classifier (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/signal/VotingClassifier.java`
- **Implementation:** "Voting Classifier" requires ILI signal to be "voted on" by at least one other ML model.
- **Example:** "ILI Buy" + "XGBoost Bullish" = `ACTIONABLE`.
- Optional `ML_CONFIRMATION` filter in `SignalGenerator`.

#### Validation Criteria

- [ ] Empirically validates that chosen ILI weights outperform standard black-box ML models in their domain.
- [ ] Identifies specific market conditions where LSTM or XGBoost provides more accurate signal than linear ILI formula.

---

### 3.4 FinRL-Meta and Explainability

**Source:** SSRN-3955949 (Liu et al., 2021)
**Tracks:** Track 3 (Analytics Worker), Track 4 (Ingestion), Track 7 (Dashboard)
**Type:** COMPLEMENTARY

#### Rationale

FinRL-Meta provides a unified financial data engineering framework and a "Training-Testing-Trading" pipeline to reduce
the simulation-to-reality gap. Explainable DRL through SHAP/LRP makes agent decisions interpretable.

#### Module: Unified Data Processor (Track 4)

- **Implementation:** Adopt FinRL-Meta design of a "unified data processor".
    - Refactor Track 4 components into plug-and-play architecture.
    - Adding a new macro source (e.g., ECB data) becomes as simple as adding a new "Environment" wrapper.
- **Data-Centric AI:** Shift focus from "fetching data" to "engineering features" for DRL agents.
    - Automated feature scaling and normalization within the ingestion pipeline.
    - Match `Gym` environment standards.
- Refactor `FredClient` and `NyFedClient` to output data in FinRL-Meta-compatible format.

#### Module: ModelExplanabilityService (Track 3)

- **Location:** `analytics/app/services/explainability/model_explainability.py`
- **Dependencies:** `shap`
- **Implementation:** Compute feature importance for each signal using SHAP (SHapley Additive exPlanations) or
  Layer-wise Relevance Propagation (LRP).
- Show which ILI component (RRP, Spread, or Vol) most influenced the DRL agent's decision.

#### Module: DRL Attribution Panel (Track 7)

- **Location:** Frontend `SignalAttribution.tsx` component in `SignalShowcase`.
- **Features:**
    - "Top 3 Drivers" for every `ACTIONABLE` DRL signal.
    - "Simulation-to-Reality Gauge": Confidence score based on `Training-Testing-Trading` pipeline results.
    - If agent performance in current "Trading" window deviates significantly from "Testing" window, flag model for
      recalibration.

#### Validation Criteria

- [ ] Dashboard correctly identifies "Top 3 Drivers" for every `ACTIONABLE` DRL signal.
- [ ] FinRL-Meta integration reduces time to add a new data source to Track 4 by > 50%.

---

## PART IV: A/B TESTING FRAMEWORK (TOURNAMENT)

All agent alternatives are designed to be tested against each other using the same backtest scenarios and evaluation
metrics.

### Tournament Configuration

| Agent                     | Algorithm                  | Action Space         | Key Differentiator            |
|---------------------------|----------------------------|----------------------|-------------------------------|
| Baseline (v2)             | Rule-based ILI percentiles | Binary BUY/SELL      | Current production            |
| FinRL PPO (PRIMARY)       | PPO                        | Continuous [-1, 1]   | Nuanced position sizing       |
| DQN Portfolio (Alt A)     | DQN/PPO                    | Discrete/Continuous  | Portfolio-level decisions     |
| TD3 Execution (Alt B)     | TD3                        | Continuous           | Optimal capital allocation    |
| Contextual Bandit (Alt C) | MTGP-LR                    | Timing only          | Execution timing optimization |
| ReAct Ensemble (Alt D)    | LangGraph LLM              | Text-based reasoning | Unstructured data integration |
| Online Learning (Alt E)   | Exponential Weights        | Weight adaptation    | Self-optimizing aggregation   |

### Evaluation Metrics

| Metric                   | Description                           | Measurement  |
|--------------------------|---------------------------------------|--------------|
| Annualized Return        | Total return over test period         | %            |
| Sharpe Ratio             | Risk-adjusted return                  | Ratio        |
| Max Drawdown             | Largest peak-to-trough decline        | %            |
| Win Rate                 | Percentage of profitable trades       | %            |
| False Positive Reduction | Signals that would have led to losses | Count        |
| Regime Adaptation Speed  | Time to adjust to new regime          | Minutes      |
| Explainability Score     | SHAP/LRP feature attribution clarity  | Qualitative  |
| Inference Latency        | Time from state observation to action | Milliseconds |

### Complementary Module Cross-Reference

The complementary modules enhance ALL alternatives:

| Complementary Module        | Benefits to All Agents                                          |
|-----------------------------|-----------------------------------------------------------------|
| ML Price Prediction         | Provides signal confidence filter for any agent                 |
| Quant Finance Trends        | LSTM volatility forecast supplements any agent's state space    |
| Multi-Model Benchmark       | Tournament framework enables fair comparison                    |
| FinRL-Meta + Explainability | Data pipeline standardization + interpretability for all agents |

---

## Source Proposals

| #  | Original Proposal             | File                                        | Section           |
|----|-------------------------------|---------------------------------------------|-------------------|
| 1  | FinRL Adaptive Trading Agent  | `FINRL_ADAPTIVE_TRADING_AGENT_PROPOSAL.md`  | Core 1.1          |
| 2  | RL Adaptive Portfolio         | `RL_ADAPTIVE_PORTFOLIO_PROPOSAL.md`         | Alternative A 2.1 |
| 3  | DRL Execution Strategy        | `DRL_EXECUTION_STRATEGY_PROPOSAL.md`        | Alternative B 2.2 |
| 4  | Contextual Bandit Execution   | `CONTEXTUAL_BANDIT_EXECUTION_PROPOSAL.md`   | Alternative C 2.3 |
| 5  | AI Trader Insights            | `AI_TRADER_INSIGHTS_PROPOSALS.md`           | Alternative D 2.4 |
| 6  | Online Learning               | `ONLINE_LEARNING_PROPOSAL.md`               | Alternative E 2.5 |
| 7  | ML Price Prediction           | `ML_PRICE_PREDICTION_PROPOSAL.md`           | Complementary 3.1 |
| 8  | ML Quant Finance Trends       | `ML_QUANT_FINANCE_TRENDS_PROPOSAL.md`       | Complementary 3.2 |
| 9  | Multi-Model Benchmark         | `MULTI_MODEL_BENCHMARK_PROPOSAL.md`         | Complementary 3.3 |
| 10 | FinRL-Meta and Explainability | `FINRL_META_AND_EXPLAINABILITY_PROPOSAL.md` | Complementary 3.4 |
