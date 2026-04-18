# Proposal: Quantitative Trading Algorithm Framework

## Overview

File Ref: `pdf-ssrn/ssrn-6553778.pdf`
This proposal summarizes insights from the paper "Quantitative Trading Algorithm" (Gideonsson, 2025) for improving the
demo-stage verification of the Tickonomics platform (Track 10).

## Applicable Insights

1. **Performance Flexibility:** The paper highlights that algorithmic performance varies significantly across market
   conditions, emphasizing the need for strategy flexibility.
2. **Evaluation Metrics:** The paper successfully demonstrates the use of Sharpe ratio (3.32) and maximum drawdown (
   5.43%) as core metrics for quantitative strategy evaluation.
3. **Hybrid Model Approach:** The final model, which introduced AI-driven sentiment analysis, showed that adding diverse
   data sources significantly enhances performance.

## Proposed Actions

1. **Enhance Demo Dashboard:** Use the paper's portfolio performance metrics as a benchmark for our
   `SignalQualityReport` and `DemoDashboard`.
2. **Strategy Flexibility:** Build more flexibility into our `SignalGenerator` filters to automatically adjust
   thresholds based on regime detection, mimicking the paper's emphasis on flexibility.
3. **Sentiment Analysis:** Explore adding sentiment-based indicators (e.g., via LLM intelligence) to our signal pipeline
   to complement the liquidity-based strategy.

## Recommendation

Incorporate the paper's successful model benchmarking metrics into the Demo phase (Track 10) as standard KPIs for all
virtual portfolio experiments.
