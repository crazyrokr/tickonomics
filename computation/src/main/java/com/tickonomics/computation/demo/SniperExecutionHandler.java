package com.tickonomics.computation.demo;

import com.tickonomics.computation.kpi.SignalResult;
import org.springframework.stereotype.Component;

/**
 * Aggressive (sniper) execution: cross the spread at threshold crossing with full adverse
 * slippage applied in the signal's direction — the worst-case cost outcome for the dual-portfolio
 * comparison.
 */
@Component
public class SniperExecutionHandler {

  public FillEstimate fill(SignalResult signal, double referencePrice, double aggressiveSlippageBps) {
    double multiplier = aggressiveSlippageBps / 10_000.0;
    double fillPrice = SignalResult.DIR_SELL.equals(signal.direction())
        ? referencePrice * (1.0 - multiplier)
        : referencePrice * (1.0 + multiplier);
    return new FillEstimate("SNIPER", fillPrice, aggressiveSlippageBps, true);
  }
}
