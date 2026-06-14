package com.tickonomics.computation.demo;

import org.springframework.stereotype.Component;

/**
 * Pre-trade systemic-impact check using a square-root market-impact proxy: impact scales with the
 * square root of the participation rate. {@link PaperTradingEngine} blocks orders whose estimated
 * impact exceeds the configured bps threshold.
 */
@Component
public class OrderImpactPredictor {

  static final double SQRT_CONSTANT = 0.005;

  public boolean isAcceptable(double orderNotional, double liquidityNotional,
      double maxImpactBps) {
    if (liquidityNotional <= 0.0) {
      return false;
    }
    if (orderNotional <= 0.0) {
      return true;
    }
    return estimatedImpactBps(orderNotional, liquidityNotional) <= maxImpactBps;
  }

  public double estimatedImpactBps(double orderNotional, double liquidityNotional) {
    if (liquidityNotional <= 0.0 || orderNotional <= 0.0) {
      return 0.0;
    }
    double participationRate = orderNotional / liquidityNotional;
    return SQRT_CONSTANT * Math.sqrt(participationRate) * 10_000.0;
  }
}
