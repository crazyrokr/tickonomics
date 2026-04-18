# ADR-001: Integration of "151 Trading Strategies" Encyclopedia

## Status

Proposed

## Context

Tickonomics is designed as a high-performance market monitoring and algorithmic trading platform. To differentiate
itself and provide maximum value to users (quants, traders, and institutional monitors), it requires a robust and
mathematically sound library of trading strategies.

The paper "151 Trading Strategies" by Zura Kakushadze and Juan Andrés Serur (SSRN-3247865) provides a comprehensive
encyclopedia of over 150 trading strategies across all major asset classes, including mathematical formulas and
backtesting methodologies.

## Decision

We will systematically integrate the "151 Trading Strategies" encyclopedia as a foundational reference for the
Tickonomics platform, with a **Math-First** implementation priority.

### Architectural Impact:

1. **Math-First Strategy Implementation (Priority 1):**
    - Priority is given to the 550+ mathematical formulas and closed-form equations.
    - We will implement a category-based interface system in the `computation` module (e.g., `OptionStrategy`,
      `MeanReversionStrategy`, `FixedIncomeStrategy`) to house the ported formulas.
    - All strategies will output a standardized `AlphaSignal` type compatible with the `SignalGenerator` and
      `AlertManager`.

2. **Machine Learning Integration (Priority 2 / Plan B):**
    - ML-based strategies (KNN, ANN, Sentiment Analysis) are relegated to a future phase.
    - The architecture remains "ML-Ready" via the `AnalyticsWorker`, but foundational rigor will be established using
      pure quantitative math first.

3. **Data Model Extension (CDM):**
   ...
    - The Common Data Model (CDM) will be extended to support the specific inputs required by these strategies, such as:
        - **Options:** Greeks (Delta, Gamma, Vega, Theta), Time-to-Maturity (TTM), Implied Volatility.
        - **Fixed Income:** Macaulay/Modified Duration, Convexity, Credit Ratings, Yield Spreads.
        - **Commodities:** Hedging Pressure (HP), Roll Yield factors.
        - **Futures:** Open Interest, Basis statistics.

3. **Analytics Worker Delegation:**
    - Formulaic Alphas (Section 3.20) and complex econometric checks (ADF, Granger) will be delegated to the Python
      Analytics Worker using `FinanceToolkit` and `OpenBB`.
    - Pure mathematical rolling statistics will be implemented in Java using `TA-Lib`.

4. **Backtesting Standardization:**
    - The `backtesting` module will adopt the "Delay-d" backtest methodology described in the paper's Appendix to
      strictly avoid look-ahead bias and model realistic execution delays.

## Consequences

### Positive:

- **Comprehensive Library:** Immediate access to a wide range of established quantitative strategies.
- **Mathematical Rigor:** Strategy implementation will be based on peer-reviewed formal definitions.
- **Multi-Asset Capability:** Positions Tickonomics as a true multi-asset platform.

### Negative / Challenges:

- **Data Requirements:** Increased complexity in data ingestion to fetch the necessary "enrichment" data (Greeks,
  Durations).
-
    - **Computation Load:** Some strategies (e.g., Alpha Combos, KNN) are computationally intensive and will require
      careful optimization using Java 25 Virtual Threads.

## References

- `pdf-ssrn/ssrn-3247865.pdf` (151 Trading Strategies)
- `docs/proposals/references/PROPOSAL_151_TRADING_STRATEGIES.md`
- `docs/plan_v4/05-computation-engine.md`
