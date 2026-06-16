package com.tickonomics.cdm.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record CdmTick(
    Instant time, String symbol, BigDecimal price, long volume, int[] conditions) {
  public CdmTick {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(symbol, "symbol must not be null");
    Objects.requireNonNull(price, "price must not be null");

    if (price.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("price must be positive");
    }
    if (volume < 0) {
      throw new IllegalArgumentException("volume must be non-negative");
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
    if (!(o instanceof CdmTick other)) {
      return false;
    }
    return Objects.equals(time, other.time)
        && Objects.equals(symbol, other.symbol)
        && price.compareTo(other.price) == 0
        && volume == other.volume
        && Arrays.equals(conditions, other.conditions);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(time, symbol, price, volume);
    result = 31 * result + Arrays.hashCode(conditions);
    return result;
  }
}
