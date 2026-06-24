package com.tickonomics.cdm.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Canonical prediction-market quote. {@code outcomeYesPrice} is the market-implied probability of the
 * yes outcome in [0, 1]. Consumed by the perspective-mismatch engine alongside {@link CdmNewsEvent}.
 */
public record CdmPredictionMarketQuote(
    Instant time, String marketId, String question,
    double outcomeYesPrice, double volume, double liquidity, String source) {
  public CdmPredictionMarketQuote {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(marketId, "marketId must not be null");
    Objects.requireNonNull(question, "question must not be null");
    Objects.requireNonNull(source, "source must not be null");
    if (!Double.isFinite(outcomeYesPrice) || outcomeYesPrice < 0.0 || outcomeYesPrice > 1.0) {
      throw new IllegalArgumentException("outcomeYesPrice must be a probability in [0, 1]");
    }
    if (!Double.isFinite(volume) || volume < 0.0) {
      throw new IllegalArgumentException("volume must be non-negative");
    }
    if (!Double.isFinite(liquidity) || liquidity < 0.0) {
      throw new IllegalArgumentException("liquidity must be non-negative");
    }
  }
}
