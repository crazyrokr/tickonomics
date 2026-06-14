package com.tickonomics.web.realtime;

import java.time.Instant;
import java.util.UUID;

/**
 * Alpha signal notification broadcast on the {@code /ws/signals} channel.
 * Shape mirrors the {@code SignalNotification} message schema in {@code asyncapi.yaml}.
 */
public record SignalNotification(
    UUID id,
    Instant timestamp,
    String symbol,
    String direction,
    UUID strategyId,
    double strength,
    double confidence) {

  public static SignalNotification of(UUID id, String symbol, String direction) {
    return new SignalNotification(id, Instant.now(), symbol, direction, null, 0.0, 0.0);
  }
}
