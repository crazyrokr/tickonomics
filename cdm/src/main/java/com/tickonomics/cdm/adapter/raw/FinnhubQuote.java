package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw Finnhub real-time equity quote. Deserialized from Finnhub /quote endpoint.
 */
public record FinnhubQuote(
    Instant time, String symbol,
    double currentPrice, double high, double low, double open, double previousClose, long volume) {
  public FinnhubQuote {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(symbol, "symbol must not be null");
    if (currentPrice <= 0) {
      throw new IllegalArgumentException("currentPrice must be positive");
    }
    if (volume < 0) {
      throw new IllegalArgumentException("volume must be non-negative");
    }
  }
}
