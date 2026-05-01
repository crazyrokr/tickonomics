package com.tickonomics.computation.backtest;

import java.util.ArrayList;
import java.util.List;

public class DelayDExecutor {

  private static final double FRAGILITY_THRESHOLD = 3.0;

  public List<BacktestResult> compareDelays(
      String strategyName,
      List<Double> signalReturns,
      List<Double> executionPrices) {
    List<BacktestResult> results = new ArrayList<>();

    BacktestResult delay0 = executeWithDelay(strategyName, ExecutionDelay.DELAY_0, signalReturns, executionPrices);
    BacktestResult delay1 = executeWithDelay(strategyName, ExecutionDelay.DELAY_1, signalReturns, executionPrices);

    if (delay1.sharpeRatio() != 0 && Math.abs(delay0.sharpeRatio() / delay1.sharpeRatio()) > FRAGILITY_THRESHOLD) {
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

    results.add(delay0);
    results.add(delay1);
    return results;
  }

  private BacktestResult executeWithDelay(
      String strategyName,
      ExecutionDelay delay,
      List<Double> signalReturns,
      List<Double> executionPrices) {
    int offset = delay.days();
    int n = Math.min(signalReturns.size(), executionPrices.size()) - offset;

    if (n <= 0) {
      return new BacktestResult(strategyName, delay, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, List.of());
    }

    double cumReturn = 0.0;
    double maxCum = 0.0;
    double maxDD = 0.0;
    int wins = 0;

    for (int i = 0; i < n; i++) {
      double ret = signalReturns.get(i);
      double execRet = executionPrices.get(i + offset);
      cumReturn += execRet;
      if (execRet > 0) {
        wins++;
      }
      maxCum = Math.max(maxCum, cumReturn);
      maxDD = Math.max(maxDD, maxCum - cumReturn);
    }

    double mean = cumReturn / n;
    double variance = 0.0;
    for (int i = 0; i < n; i++) {
      double execRet = executionPrices.get(i + offset);
      variance += Math.pow(execRet - mean, 2);
    }
    variance /= n;
    double sharpe = Math.sqrt(variance) > 0 ? mean / Math.sqrt(variance) : 0.0;

    return new BacktestResult(
        strategyName,
        delay,
        sharpe,
        cumReturn,
        maxDD,
        n > 0 ? (double) wins / n : 0.0,
        cumReturn,
        cumReturn,
        0.0,
        false,
        List.of());
  }
}
