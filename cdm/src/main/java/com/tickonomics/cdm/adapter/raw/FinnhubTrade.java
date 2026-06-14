package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Normalized real-time equity trade event from WebSocket feeds. Provider-agnostic — adapters map
 * provider-specific trade formats to this record.
 */
public record FinnhubTrade(
    Instant time, String symbol, double price, long volume, double tickVolume) {
  public FinnhubTrade {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(symbol, "symbol must not be null");
    if (price <= 0) {
      throw new IllegalArgumentException("price must be positive");
    }
    if (volume < 0) {
      throw new IllegalArgumentException("volume must be non-negative");
    }
  }
}
