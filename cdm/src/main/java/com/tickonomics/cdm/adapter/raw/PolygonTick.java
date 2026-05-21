package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw Polygon WebSocket tick. Deserialized directly from Polygon WS JSON messages.
 */
public record PolygonTick(
    Instant time, String symbol, double price, long volume, int[] conditions) {
  public PolygonTick {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(symbol, "symbol must not be null");
    conditions = conditions != null ? conditions.clone() : null;
  }

  @Override
  public int[] conditions() {
    return conditions != null ? conditions.clone() : null;
  }
}
