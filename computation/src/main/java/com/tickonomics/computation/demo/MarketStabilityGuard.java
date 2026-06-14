package com.tickonomics.computation.demo;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker guard over aggregate portfolio volatility. {@link PaperTradingEngine} halts new
 * entries while {@link #isStable(List, double)} returns false. The check is pure/stateless so the
 * halt decision is deterministic and testable; operational cooldown hysteresis is a caller concern.
 *
 * <p>Returns are fractional (0.01 == 1%); the threshold is expressed in percent (5.0 == 5%).
 */
@Component
public class MarketStabilityGuard {

  public boolean isStable(List<Double> recentReturns, double volatilityThresholdPct) {
    return volatilityPct(recentReturns) <= volatilityThresholdPct;
  }

  public double volatilityPct(List<Double> recentReturns) {
    if (recentReturns == null || recentReturns.size() < 2) {
      return 0.0;
    }
    double mean = recentReturns.stream().mapToDouble(d -> d).average().orElse(0.0);
    double variance = recentReturns.stream()
        .mapToDouble(d -> d)
        .map(r -> r - mean)
        .map(r -> r * r)
        .average()
        .orElse(0.0);
    return Math.sqrt(variance) * 100.0;
  }
}
