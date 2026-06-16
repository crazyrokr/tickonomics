package com.tickonomics.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

public record TickData(
    Instant time, String symbol, BigDecimal price, long volume, int[] conditions) {
  public TickData {
    if (time == null) {
      throw new NullPointerException("time must not be null");
    }
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
    if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("price must be positive");
    }
    if (volume < 0) {
      throw new IllegalArgumentException("volume must not be negative");
    }
  }
}
