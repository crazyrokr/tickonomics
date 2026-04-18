# Proposal: Loan Portfolio Optimization Methodologies Synthesis

## Overview

File Ref: `pdf-ssrn/ssrn-5329963.pdf`
This proposal suggests incorporating insights from the systematic literature review "Mathematics for Finance: A Review
of Quantitative Methods in Loan Portfolio Optimization" (Kowsar et al., 2023) into the Tickonomics v2 Analytics (Track
3) and Computation (Track 5) engines.

## Applicable Insights

1. **Stochastic Optimization:** The review reinforces our choice of stochastic programming as a benchmark for modeling
   credit transitions and macroeconomic volatility.
2. **Hybrid Models (Stochastic + ML):** The review highlights the growing trend of combining stochastic frameworks with
   machine learning (e.g., neural networks, gradient boosting) for default probability prediction.
3. **Regulatory Integration:** The review documents the embedding of Basel III RWA and stress-testing protocols directly
   into optimization objectives, which aligns with our existing Liquidity Stress Index (LSI) and proxy divergence
   monitoring.

## Proposed Actions

1. **Enhance AIC Lag Selection:** Incorporate the review's emphasis on multi-stage programming by exploring more
   granular lag models in the `aic_service.py` within the Python worker.
2. **Expand ML Features:** Explore integrating the review's recommended ensemble methods (gradient boosting) for
   liquidity regime classification (improving upon the existing k-means approach in `RegimeDetector`).
3. **Basel Alignment:** Ensure the `Persistence` schema and KPI engine continue to align with the RWA and capital
   adequacy metrics synthesized in the literature review for future regulatory-grade auditability.

## Recommendation

Integrate these findings into the computation engine's research backlog for Phase 2/3 and consider gradient boosting as
a potential upgrade for the `RegimeDetector`.
