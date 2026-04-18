# Proposal: Quantitative Finance Course Integration into Tickonomics

## Summary

The PDF `pdf-ssrn/ssrn-5178205.pdf` contains comprehensive lecture notes by Eric Vansteenberghe titled "Quantitative
methods in finance" (2026). These notes cover a vast array of topics relevant to quantitative finance, including Python
programming, econometrics, time series modeling, machine learning in finance, extreme value theory (EVT), and Bayesian
statistics.

## Applicability to Tickonomics

These lecture notes are highly applicable to Tickonomics as they provide the theoretical and implementation foundation
for many of the modules currently planned or in development, specifically:

- **Analytics & Computation:** The notes cover SARIMA, GARCH models, VAR, SVAR, and quantile regression, which directly
  align with the computation engine (Track 5) and backtesting framework (Track 8).
- **Extreme Value Theory:** Sections on EVT and VaR backtesting provide a rigorous framework for the
  `LIQUIDITY_STRESS_TEST_MODULE_PROPOSAL.md`.
- **Machine Learning Integration:** The notes offer a pragmatic perspective on when to use parametric econometrics vs.
  machine learning, which is central to the `ML_PRICE_PREDICTION_PROPOSAL.md` and `DL_REGIME_DETECTION_PROPOSAL.md`.

## Action Plan

1. **Curriculum Mapping:** Use these notes as a primary reference for the implementation of the computation engine (
   Track 5) and backtesting framework (Track 8).
2. **Methodological Consistency:** Ensure that the econometric implementations in `analytics/` (Python) and
   `computation/` (Java) align with the methodologies presented in the notes (e.g., proper unit root tests, GARCH
   specification).
3. **Internal Documentation:** Reference specific sections of the Vansteenberghe lecture notes in the ADRs (Architecture
   Design Records) for the `computation/` and `analytics/` modules to maintain theoretical clarity and reproducibility.
