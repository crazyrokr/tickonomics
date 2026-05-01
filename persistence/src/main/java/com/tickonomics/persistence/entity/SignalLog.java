package com.tickonomics.persistence.entity;

import java.time.Instant;

public record SignalLog(
    Instant createdAt,
    String symbol,
    String direction,
    String status,
    double iliPercentile,
    double iliValue,
    double expectedMove,
    double estimatedCost,
    String signalMetadata) {
  public SignalLog {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
    if (direction == null || direction.isBlank()) {
      throw new IllegalArgumentException("direction must not be blank");
    }
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("status must not be blank");
    }
  }
}
