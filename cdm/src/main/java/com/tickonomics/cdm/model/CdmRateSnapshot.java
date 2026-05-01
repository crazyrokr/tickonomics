package com.tickonomics.cdm.model;

import com.tickonomics.cdm.enums.InstrumentType;

import java.time.Instant;
import java.util.Objects;

public record CdmRateSnapshot(
    Instant time, InstrumentType instrumentType, double value, String source) {
  public CdmRateSnapshot {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(instrumentType, "instrumentType must not be null");
    Objects.requireNonNull(source, "source must not be null");

    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("value must be finite");
    }
  }
}
