package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw Polymarket market quote. The yes-outcome share price is a probability in [0, 1]; volume and
 * liquidity are in USDC. Provider-agnostic representation consumed by the perspective-mismatch engine.
 */
public record PolymarketQuote(
    Instant time, String marketId, String slug, String question,
    double outcomeYesPrice, double volume, double liquidity, String source) {
  public PolymarketQuote {
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
