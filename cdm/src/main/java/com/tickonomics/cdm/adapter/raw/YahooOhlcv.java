package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw Yahoo Finance OHLCV data point. Deserialized from Yahoo v8 chart response.
 */
public record YahooOhlcv(
    Instant time, String symbol,
    double open, double high, double low, double close, long volume) {
  public YahooOhlcv {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(symbol, "symbol must not be null");
    if (close <= 0) {
      throw new IllegalArgumentException("close must be positive");
    }
    if (volume < 0) {
      throw new IllegalArgumentException("volume must be non-negative");
    }
  }
}
