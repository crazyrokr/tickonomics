package com.tickonomics.persistence.entity;

import java.time.LocalDate;

/**
 * A single daily close from the {@code ohlcv_1d} continuous aggregate, used for hit-rate,
 * vs-SPY, and leverage-rotation lookups in the demo portfolio.
 */
public record DailyClose(LocalDate day, double close) {

  public DailyClose {
    if (day == null) {
      throw new IllegalArgumentException("day must not be null");
    }
    if (close <= 0) {
      throw new IllegalArgumentException("close must be positive");
    }
  }
}
