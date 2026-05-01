package com.tickonomics.computation.strategy;

public interface BaseStrategy<T> extends Strategy {
  AlphaSignal compute(T input, StrategyContext ctx);
}
