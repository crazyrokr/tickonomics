package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw NY Fed API rate response. Deserialized directly from NY Fed REST API JSON.
 */
public record NyFedRateResponse(
    Instant time, String rateType, double value, String source) {
  public NyFedRateResponse {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(rateType, "rateType must not be null");
    Objects.requireNonNull(source, "source must not be null");
  }
}
