package com.tickonomics.computation.backtest;

import java.util.List;
import java.util.Map;

public record BacktestResult(
    String strategyName,
    ExecutionDelay delay,
    double sharpeRatio,
    double totalReturn,
    double maxDrawdown,
    double winRate,
    double idealReturn,
    double adjustedReturn,
    double slippageCostBps,
    boolean liquidityFragile,
    List<Map<String, Object>> tradeLog
) {}
