package com.tickonomics.persistence.entity;

import java.time.Instant;
import java.util.UUID;

public record AlphaSignalRecord(
    Instant time,
    UUID strategyId,
    String symbol,
    String direction,
    double strength,
    double confidence,
    Double expectedMove,
    String metadata) {

  public AlphaSignalRecord {
    if (time == null) {
      throw new IllegalArgumentException("time must not be null");
    }
    if (strategyId == null) {
      throw new IllegalArgumentException("strategyId must not be null");
    }
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
    if (strength < 0 || strength > 1) {
      throw new IllegalArgumentException("strength must be between 0 and 1");
    }
    if (confidence < 0 || confidence > 1) {
      throw new IllegalArgumentException("confidence must be between 0 and 1");
    }
  }
}
