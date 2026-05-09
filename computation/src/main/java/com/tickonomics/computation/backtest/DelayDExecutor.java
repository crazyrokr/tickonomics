package com.tickonomics.computation.backtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DelayDExecutor {

  private static final double FRAGILITY_THRESHOLD = 3.0;

  private final Eq553SlippageModel slippageModel;

  public DelayDExecutor(Eq553SlippageModel slippageModel) {
    this.slippageModel = slippageModel;
  }

  public List<BacktestResult> compareDelays(
      String strategyName,
      List<Double> signalReturns,
      List<Double> executionPrices) {
    return compareDelays(strategyName, signalReturns, executionPrices,
        0.20, 1_000_000.0, 100.0);
  }

  public List<BacktestResult> compareDelays(
      String strategyName,
      List<Double> signalReturns,
      List<Double> executionPrices,
      double volatility,
      double addvDollarVolume,
      double sharesTraded) {

    BacktestResult delay0 = executeWithDelay(strategyName, ExecutionDelay.DELAY_0,
        signalReturns, executionPrices, volatility, addvDollarVolume, sharesTraded);
    BacktestResult delay1 = executeWithDelay(strategyName, ExecutionDelay.DELAY_1,
        signalReturns, executionPrices, volatility, addvDollarVolume, sharesTraded);

    if (delay1.sharpeRatio() != 0
        && Math.abs(delay0.sharpeRatio() / delay1.sharpeRatio()) > FRAGILITY_THRESHOLD) {
      delay1 = new BacktestResult(
          delay1.strategyName(),
          delay1.delay(),
          delay1.sharpeRatio(),
          delay1.totalReturn(),
          delay1.maxDrawdown(),
          delay1.winRate(),
          delay1.idealReturn(),
          delay1.adjustedReturn(),
          delay1.slippageCostBps(),
          true,
          delay1.tradeLog());
    }

    return List.of(delay0, delay1);
  }

  private BacktestResult executeWithDelay(
      String strategyName,
      ExecutionDelay delay,
      List<Double> signalReturns,
      List<Double> executionPrices,
      double volatility,
      double addvDollarVolume,
      double sharesTraded) {

    int offset = delay.days();
    int n = Math.min(signalReturns.size(), executionPrices.size()) - offset;

    if (n <= 0) {
      return new BacktestResult(strategyName, delay, 0.0, 0.0, 0.0, 0.0,
          0.0, 0.0, 0.0, false, List.of());
    }

    double cumReturn = 0.0;
    double maxCum = 0.0;
    double maxDD = 0.0;
    int wins = 0;
    double totalSlippageBps = 0.0;
    double totalAdjustedReturn = 0.0;
    List<Map<String, Object>> tradeLog = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      double signalReturn = signalReturns.get(i);
      double execReturn = executionPrices.get(i + offset);
      double slippageBps = slippageModel.calculateSlippageBps(
          volatility, addvDollarVolume, sharesTraded);
      double adjustedReturn = slippageModel.adjustReturn(
          signalReturn, volatility, addvDollarVolume, sharesTraded);

      cumReturn += execReturn;
      totalSlippageBps += slippageBps;
      totalAdjustedReturn += adjustedReturn;

      if (execReturn > 0) {
        wins++;
      }
      maxCum = Math.max(maxCum, cumReturn);
      maxDD = Math.max(maxDD, maxCum - cumReturn);

      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("index", i);
      entry.put("signalReturn", signalReturn);
      entry.put("executionReturn", execReturn);
      entry.put("slippageBps", slippageBps);
      entry.put("adjustedReturn", adjustedReturn);
      tradeLog.add(entry);
    }

    double mean = cumReturn / n;
    double variance = 0.0;
    for (int i = 0; i < n; i++) {
      double execReturn = executionPrices.get(i + offset);
      variance += Math.pow(execReturn - mean, 2);
    }
    variance /= n;
    double sharpe = Math.sqrt(variance) > 0 ? mean / Math.sqrt(variance) : 0.0;

    double avgSlippageBps = totalSlippageBps / n;
    boolean fragile = slippageModel.isLiquidityFragile(cumReturn, totalAdjustedReturn);

    return new BacktestResult(
        strategyName,
        delay,
        sharpe,
        cumReturn,
        maxDD,
        n > 0 ? (double) wins / n : 0.0,
        cumReturn,
        totalAdjustedReturn,
        avgSlippageBps,
        fragile,
        tradeLog);
  }
}
