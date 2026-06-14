package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw Alpha Vantage adjusted daily OHLCV bar. Deserialized from TIME_SERIES_DAILY_ADJUSTED response.
 * Includes split coefficient and dividend amount for adjusted close verification.
 */
public record AlphaVantageDailyBar(
    Instant time, String symbol,
    double open, double high, double low, double close, double adjustedClose,
    long volume, double dividendAmount, double splitCoefficient) {
  public AlphaVantageDailyBar {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(symbol, "symbol must not be null");
    if (adjustedClose <= 0) {
      throw new IllegalArgumentException("adjustedClose must be positive");
    }
    if (volume < 0) {
      throw new IllegalArgumentException("volume must be non-negative");
    }
  }
}
