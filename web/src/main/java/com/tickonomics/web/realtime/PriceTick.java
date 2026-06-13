package com.tickonomics.web.realtime;

import java.time.Instant;
import java.util.List;

/**
 * Normalized equity price tick broadcast on the {@code /ws/prices} channel.
 * Shape mirrors the {@code PriceTick} message schema in {@code asyncapi.yaml}.
 */
public record PriceTick(
    String symbol,
    double price,
    double volume,
    Instant timestamp,
    List<String> conditions,
    String source) {

  public static PriceTick of(String symbol, double price, Instant timestamp) {
    return new PriceTick(symbol, price, 0.0, timestamp, List.of(), "tickonomics");
  }
}
