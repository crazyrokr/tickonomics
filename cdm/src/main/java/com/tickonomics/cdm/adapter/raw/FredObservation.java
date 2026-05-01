package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw FRED API observation response. Deserialized directly from FRED REST API JSON.
 */
public record FredObservation(
    Instant time, String seriesId, double value, String source) {
  public FredObservation {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(seriesId, "seriesId must not be null");
    Objects.requireNonNull(source, "source must not be null");
  }
}
