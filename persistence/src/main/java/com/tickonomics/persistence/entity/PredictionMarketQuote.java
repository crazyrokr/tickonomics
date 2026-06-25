package com.tickonomics.persistence.entity;

import java.time.Instant;

/**
 * Persisted prediction-market quote. {@code outcomeYesPrice} is the market-implied probability of the
 * yes outcome in [0, 1]. Backs the {@code prediction_market_quotes} hypertable consumed by the
 * perspective-mismatch engine.
 */
public record PredictionMarketQuote(
    Instant time,
    String marketId,
    String question,
    double outcomeYesPrice,
    double volume,
    double liquidity,
    String source) {
  public PredictionMarketQuote {
    if (time == null) {
      throw new NullPointerException("time must not be null");
    }
    if (marketId == null || marketId.isBlank()) {
      throw new IllegalArgumentException("marketId must not be blank");
    }
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("question must not be blank");
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("source must not be blank");
    }
    if (Double.isNaN(outcomeYesPrice)) {
      throw new IllegalArgumentException("outcomeYesPrice must not be NaN");
    }
  }
}
