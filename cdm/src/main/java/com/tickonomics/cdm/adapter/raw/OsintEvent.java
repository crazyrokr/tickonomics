package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw OSINT news event (GDELT 2.0 shape). {@code avgTone} is the GDELT average tone (typically in
 * [-15, +15]); {@code themes} and {@code actors} are semicolon-delimited GDELT theme/actor codes.
 */
public record OsintEvent(
    Instant time, String eventId, String source, String headline,
    double avgTone, String themes, String actors, String url) {
  public OsintEvent {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(headline, "headline must not be null");
    if (!Double.isFinite(avgTone)) {
      throw new IllegalArgumentException("avgTone must be finite");
    }
  }
}
