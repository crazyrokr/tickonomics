# Consolidated Proposal: Fixed Income & Derivatives Analytics Module

**Status:** Consolidated Proposal for Tickonomics v3
**Target Tracks:** Track 3 (Analytics Worker), Track 5 (Computation Engine), Track 8 (Backtesting), Track 10 (
Demo/Virtual Portfolio)

---

## v3 Integration Points

| Track    | Module                 | Enhancement                                                                                        |
|:---------|:-----------------------|:---------------------------------------------------------------------------------------------------|
| Track 3  | Analytics Worker       | Sturm-Liouville spectral solver, BSM Greeks, Q-World bond pricer                                   |
| Track 5  | Computation Engine     | Yield curve stress testing, derivative pricing, sensitivity library, risk-premium residual monitor |
| Track 8  | Backtesting Framework  | Spectral approximation replacing Monte Carlo, CVA/FVA backtest scenarios                           |
| Track 10 | Demo/Virtual Portfolio | Adjusted NAV, ValuationAdjustmentEngine, Greeks-adjusted risk premium                              |

---

## Core Proposal: Spectral Bond Engine (SPECTRAL_BOND_ENGINE)

**Source:** SSRN-5047749 - "Sturm-Liouville Theory in Quantitative Finance"

**Objective:** Incorporate Sturm-Liouville (SL) spectral decomposition for bond pricing and yield curve modeling,
providing a computationally efficient and mathematically rigorous alternative to traditional finite-difference or Monte
Carlo methods.

### Theoretical Basis

One-factor interest rate models (Vasicek, Hull-White) can be cast into a self-adjoint Sturm-Liouville form. This
enables:

- **Spectral Decomposition:** Bond prices represented as infinite sums of eigenfunctions, enabling near-instant
  valuation once eigenvalues are computed.
- **Analytical Sensitivities:** Direct computation of Delta, Gamma, and Theta by differentiating the spectral expansion.
- **High-Dimensional Scaling:** Better handling of multi-asset baskets and stochastic volatility (Heston/SABR) than
  traditional numerical methods.

### Module: Spectral Analytics Service (Track 3 - Python Analytics Worker)

- New service utilizing `scipy.sparse.linalg` to solve eigenvalue problems from SL transformation of interest rate PDEs.
- File: `sturm_liouville_service.py` in the FastAPI worker.
- Exposes endpoint: `/fixed-income/spectral-bond-analytics` for use by the Java backend.

### 5-Step PDE Transformation

1. **Time transformation:** Convert time-dependent coefficients to stationary form.
2. **Discounting transformation:** Remove discount factor from the operator.
3. **Log transformation:** Convert to log-price coordinates.
4. **Derivative elimination:** Remove cross-derivative terms.
5. **SL Form:** Cast into self-adjoint Sturm-Liouville operator form.

### High-Precision Greeks (Track 3)

- Spectral methods provide institutional-grade DV01 and convexity measures.
- Applied to the 3-month T-Bill proxy used in `IntradayProxyService`.
- Analytical Greeks derived directly from spectral expansion (no finite-difference approximation).

### Yield Curve Stress Testing (Track 5)

- SL decompositions simulate how the entire yield curve reacts to an ILI liquidity drain event.
- Goes beyond the single 3-month proxy to model full curve dynamics.
- Enables "what-if" analysis for parallel shifts, steepening, and flattening scenarios.

### Credit/Liquidity Gap Analysis (Track 5)

- Apply the paper's credit risk decomposition to model "Liquidity-Adjusted" probability of default.
- Relevant for benchmarks where credit and liquidity risk components must be separated.

### Computational Efficiency (Track 8/11)

- Replace expensive Monte Carlo simulations in `BacktestingFramework` with spectral approximations.
- Reduces compute costs and increases iteration speed for `WeightOptimizer`.
- Performance gain: 10x-100x faster convergence for sensitivity calculations compared to Monte Carlo.

### Validation Criteria

- [ ] Spectral bond pricing matches finite-difference benchmarks within 1 basis point.
- [ ] 5-step PDE transformation produces valid self-adjoint operator for Vasicek and Hull-White models.
- [ ] Eigenvalue solver converges for all standard yield curve configurations.
- [ ] Analytical Greeks match finite-difference Greeks within tolerance.
- [ ] Yield curve stress tests cover parallel shift (+/-100bp), steepening, and flattening scenarios.
- [ ] Spectral approximation provides at least 10x speed improvement over Monte Carlo in backtesting.

---

## Complementary Additions

### Q-World Integration for Derivatives Fair Value (Q_WORLD_INTEGRATION)

**Source:** SSRN-1717163 (Meucci, 2011)

**Objective:** Bridge P-world (real-world risk/portfolio) and Q-world (risk-neutral derivatives) modeling to enhance ILI
proxy robustness.

#### Theoretical Basis

Meucci distinguishes between P-world (physical measure, used for risk management and portfolio construction) and
Q-world (risk-neutral measure, used for derivatives pricing). Tickonomics currently operates entirely in the P-world.
T-Bill/SOFR spreads are derivative-like instruments of interest rate risk; Q-world techniques can detect abnormal
dislocations when P-world spreads deviate from Q-world fair prices.

#### Module: QWorldBondPricer (Track 3 - Analytics Worker)

- Implemented in Python Analytics Worker using FinanceToolkit.
- CIR (Cox, Ingersoll, Ross) model for modeling fair-value yield of 3M T-Bill.
- Heston model components for stochastic volatility treatment.
- Outputs: Q-world fair-value yield for comparison against observed market yield.

#### Risk-Premium Residual Monitor (Track 5)

- Compares observed market spread (P-world) against Q-world fair-value model output.
- Residual (Observed - Fair) exceeding defined threshold validates the "DISLOCATED" status from `ProxyDivergenceGuard`.
- Integrates into existing `ProxyDivergenceMonitor` (Track 4).

#### Validation Criteria

- [ ] Q-world fair value remains stable under normal market conditions (residual within 2 standard deviations).
- [ ] Residual check correctly flags dislocation during 2019 repo spike and March 2020.
- [ ] CIR model calibration converges on standard T-Bill historical data.

---

### CVA/FVA Valuation Adjustments (CVA_FVA_VALUATION)

**Source:** SSRN-2396545 (Pedersen, 2013)

**Objective:** Account for counterparty credit risk and funding costs in portfolio valuation through rigorous Valuation
Adjustments.

#### Module: DerivativePricingModule (Track 5)

- Provides accurate pricing for interest-rate-sensitive assets.
- Implements analytical expansion method from the thesis (Chapter 1) to price European call/put options on
  interest-rate-sensitive underlyings.
- Integrates with Spectral Bond Engine for yield curve inputs.

#### Module: ValuationAdjustmentEngine (Track 10)

- Adjusts marked-to-market value of portfolio assets by calculating:
    - **CVA (Credit Valuation Adjustment):** Expected loss from counterparty default.
    - **DVA (Debt Valuation Adjustment):** Own credit risk benefit.
    - **FVA (Funding Valuation Adjustment):** Cost/benefit of funding the position.
- `VirtualPortfolio` reports "Adjusted NAV" reflecting these adjustments.

#### Integration with Spectral Bond Engine

- Spectral engine provides high-speed yield curve inputs for CVA/FVA calculations.
- Greeks from spectral decomposition feed directly into sensitivity calculations required for CVA.
- Analytical interest rate sensitivities improve accuracy of expected exposure profiles.

#### Validation Criteria

- [ ] DerivativePricingModule matches benchmark pricing (standard Heston) within 0.5% tolerance.
- [ ] CVA/FVA calculations produce non-negative adjustments under all tested scenarios.
- [ ] Adjusted NAV reflects all three adjustments (CVA, DVA, FVA) correctly.
- [ ] ValuationAdjustmentEngine runs within the daily batch window (< 5 minutes for full portfolio).

---

### Greeks as Standardized Risk Primitives (GREEKS_RISK_PRIMITIVE)

**Source:** SSRN-5187624

**Objective:** Formalize all risk sensitivities into a standardized "Greeks" framework, ensuring signal generation logic
is explicitly sensitivity-adjusted.

#### Module: MarketSensitivityLibrary (Track 5)

- Groups current KPIs (Repo/Equity Beta, Duration, Convexity) into an explicit library.
- Package: `computation/` with `MarketSensitivityLibrary` class.
- `IliCalculator` and `SignalGenerator` reference these metrics using standardized nomenclature.

#### Standardized Nomenclature

| Traditional Name       | Greeks Nomenclature | Description                            |
|:-----------------------|:--------------------|:---------------------------------------|
| Repo/Equity Beta       | Repo-Delta          | Sensitivity to repo rate changes       |
| Duration               | Rate-Delta          | First-order interest rate sensitivity  |
| Convexity              | Rate-Gamma          | Second-order interest rate sensitivity |
| Spread Sensitivity     | Spread-Delta        | Sensitivity to credit spread changes   |
| Volatility Sensitivity | Volga               | Sensitivity to volatility changes      |

#### Sensitivity-Adjusted Signal Logic

- Signal cost model includes a sensitivity-adjusted liquidity premium.
- High-duration bonds require a larger risk premium for signal admissibility.
- `SignalGenerator` applies Greeks-adjusted thresholds before dispatching actionable signals.

#### Integration with Spectral Bond Engine

- Spectral engine provides analytical Greeks with rigorous error bounds.
- MarketSensitivityLibrary consumes these analytical sensitivities directly.
- Eliminates finite-difference approximation errors from the risk primitive framework.

#### Validation Criteria

- [ ] All existing KPIs mapped to standardized Greeks nomenclature.
- [ ] Signal generation logic correctly applies sensitivity-adjusted thresholds.
- [ ] Greeks-adjusted signals show improved risk-adjusted returns over non-adjusted signals.

---

## Module Interaction Map

```
Spectral Bond Engine (Core)
    |
    +--> Analytical Greeks (Delta, Gamma, Theta, DV01, Convexity)
    |        |
    |        v
    |   MarketSensitivityLibrary (Greeks Risk Primitive)
    |        |
    |        v
    |   SignalGenerator (sensitivity-adjusted thresholds)
    |
    +--> Yield Curve Stress Testing
    |        |
    |        v
    |   QWorldBondPricer (Q-World Integration)
    |        |
    |        v
    |   Risk-Premium Residual Monitor
    |
    +--> Fast Yield Curve Inputs
             |
             v
        DerivativePricingModule (CVA/FVA)
             |
             v
        ValuationAdjustmentEngine
             |
             v
        VirtualPortfolio (Adjusted NAV)
```

---

## Python Analytics Worker Service Map

The Python FastAPI analytics worker hosts the compute-intensive components of this module:

| Service              | Endpoint                                | Dependencies                      |
|:---------------------|:----------------------------------------|:----------------------------------|
| Spectral Analytics   | `/fixed-income/spectral-bond-analytics` | scipy.sparse.linalg               |
| Q-World Bond Pricing | `/fixed-income/q-world-fair-value`      | FinanceToolkit, CIR/Heston models |
| Greeks Calculation   | `/fixed-income/greeks`                  | Spectral engine output            |
| Derivative Pricing   | `/fixed-income/derivative-pricing`      | Spectral engine, yield curve      |

---

## Java Backend Integration

The Java 25 Spring Boot backend consumes the Python analytics worker endpoints and integrates the results into the
computation engine:

| Component                  | Package        | Role                            |
|:---------------------------|:---------------|:--------------------------------|
| MarketSensitivityLibrary   | `computation/` | Standardized risk primitives    |
| YieldCurveStressTester     | `computation/` | ILI liquidity drain simulations |
| RiskPremiumResidualMonitor | `computation/` | P-world vs Q-world comparison   |
| DerivativePricingModule    | `computation/` | Interest-rate-sensitive pricing |
| ValuationAdjustmentEngine  | `computation/` | CVA/DVA/FVA calculations        |

---

## Source Proposals

1. `SPECTRAL_BOND_ENGINE_PROPOSAL.md` - Sturm-Liouville spectral decomposition, Vasicek/Hull-White, analytical Greeks (
   SSRN-5047749)
2. `Q_WORLD_INTEGRATION_PROPOSAL.md` - P-world/Q-world bridge, CIR/Heston fair value, risk-premium residual (
   SSRN-1717163)
3. `CVA_FVA_VALUATION_PROPOSAL.md` - DerivativePricingModule, ValuationAdjustmentEngine, Adjusted NAV (SSRN-2396545)
4. `GREEKS_RISK_PRIMITIVE_PROPOSAL.md` - Standardized sensitivity framework, MarketSensitivityLibrary, Repo-Delta
   nomenclature (SSRN-5187624)
