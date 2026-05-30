package com.tickonomics.computation.backtest;

import org.springframework.stereotype.Component;

@Component
public class Eq553SlippageModel {

  private static final double ZETA = 0.15;

  public double calculateSlippageBps(double volatility, double addvDollarVolume, double sharesTraded) {
    if (addvDollarVolume <= 0) {
      return Double.MAX_VALUE;
    }
    return ZETA * (volatility / addvDollarVolume) * Math.abs(sharesTraded);
  }

  public double adjustReturn(double idealReturn, double volatility, double addvDollarVolume, double sharesTraded) {
    double slippage = calculateSlippageBps(volatility, addvDollarVolume, sharesTraded);
    double slippageDecimal = slippage / 10000.0;
    return idealReturn - slippageDecimal;
  }

  public boolean isLiquidityFragile(double idealReturn, double adjustedReturn) {
    if (idealReturn <= 0) {
      return false;
    }
    return adjustedReturn < 0;
  }
}
