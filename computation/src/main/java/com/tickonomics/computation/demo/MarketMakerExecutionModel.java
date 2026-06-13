package com.tickonomics.computation.demo;

import org.springframework.stereotype.Component;

/**
 * Algorithmic price-stability execution model: prefer passive limit orders at the fair (mid) price
 * to capture spread rather than crossing with aggressive market orders. Reduces the demo's
 * contribution to price bubbles; the captured spread is reported in the signal-quality metrics.
 */
@Component
public class MarketMakerExecutionModel {

  public enum Strategy {
    LIMIT, MARKET
  }

  public record ExecutionDecision(
      Strategy strategy,
      double limitPrice,
      double expectedFillPrice,
      double spreadCaptureBps) {}

  /**
   * @param bid                 best bid
   * @param ask                 best ask
   * @param fairPrice           ILI-derived fair price used as the passive limit reference
   * @param preferLimitOrders   when true and the quote is not crossed, place a passive limit
   */
  public ExecutionDecision decide(double bid, double ask, double fairPrice,
      boolean preferLimitOrders) {
    double mid = (bid + ask) / 2.0;
    boolean quoteValid = ask > bid && ask > 0 && bid > 0;
    if (preferLimitOrders && quoteValid) {
      double spreadCaptureBps = (mid > 0) ? ((ask - bid) / 2.0) / mid * 10_000.0 : 0.0;
      return new ExecutionDecision(Strategy.LIMIT, fairPrice, fairPrice, spreadCaptureBps);
    }
    return new ExecutionDecision(Strategy.MARKET, mid, mid, 0.0);
  }
}
