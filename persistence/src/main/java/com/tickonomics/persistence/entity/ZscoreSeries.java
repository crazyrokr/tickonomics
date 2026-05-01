package com.tickonomics.persistence.entity;

import java.time.Instant;

public record ZscoreSeries(
    Instant time, String component, double rawValue, double zScore, int lookbackDays) {
  public ZscoreSeries {
    if (time == null) {
      throw new NullPointerException("time must not be null");
    }
    if (component == null || component.isBlank()) {
      throw new IllegalArgumentException("component must not be blank");
    }
    if (lookbackDays <= 0) {
      throw new IllegalArgumentException("lookbackDays must be positive");
    }
  }
}
