package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Generic rate/price row from DataHub CSV datasets (VIX, oil, gold). Contains a timestamp and
 * a single numeric value.
 */
public record DataHubPriceRow(Instant time, double value) {
  public DataHubPriceRow {
    Objects.requireNonNull(time, "time must not be null");
    if (Double.isNaN(value)) {
      throw new IllegalArgumentException("value must not be NaN");
    }
  }
}
