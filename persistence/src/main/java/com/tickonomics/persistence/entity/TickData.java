package com.tickonomics.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

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
    conditions = conditions != null ? conditions.clone() : null;
  }

  @Override
  public int[] conditions() {
    return conditions != null ? conditions.clone() : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TickData that)) {
      return false;
    }
    return Objects.equals(time, that.time)
        && Objects.equals(symbol, that.symbol)
        && Objects.equals(price, that.price)
        && volume == that.volume
        && Arrays.equals(conditions, that.conditions);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(time, symbol, price, volume);
    result = 31 * result + Arrays.hashCode(conditions);
    return result;
  }
}
