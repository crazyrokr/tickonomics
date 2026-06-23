package com.tickonomics.persistence.entity;

import java.time.Instant;

public record CorrelationOutput(
    Instant time,
    String symbol,
    String metric,
    Double correlation,
    Double pValue,
    Integer sampleSize,
    Integer lagOrder,
    String direction) {
  public CorrelationOutput {
    if (time == null) {
      throw new NullPointerException("time must not be null");
    }
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
    if (metric == null || metric.isBlank()) {
      throw new IllegalArgumentException("metric must not be blank");
    }
  }
}
