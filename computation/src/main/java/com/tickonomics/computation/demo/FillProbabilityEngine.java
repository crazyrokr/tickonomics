package com.tickonomics.computation.demo;

import org.springframework.stereotype.Component;

/**
 * Models "phantom liquidity" via the Phantom Liquidity Indicator (PLI) in {@code [0,1]}: 0 means
 * the quote is real, 1 means it is entirely phantom. Higher PLI lowers the simulated fill
 * probability and inflates the slippage multiplier applied by {@link PaperTradingEngine}.
 */
@Component
public class FillProbabilityEngine {

  public double fillProbability(double pli) {
    return 1.0 - clamp(pli);
  }

  public double slippageMultiplier(double pli) {
    return 1.0 + clamp(pli);
  }

  private static double clamp(double pli) {
    if (pli < 0.0) {
      return 0.0;
    }
    return Math.min(pli, 1.0);
  }
}
