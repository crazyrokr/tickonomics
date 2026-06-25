package com.tickonomics.cdm.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Canonical OSINT news event. {@code avgTone} is the news-tone signal (GDELT average tone) compared
 * against prediction-market-implied probability by the perspective-mismatch engine.
 */
public record CdmNewsEvent(
    Instant time, String eventId, String source, String headline,
    double avgTone, String themes, String actors, String url) {
  public CdmNewsEvent {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(headline, "headline must not be null");
    if (!Double.isFinite(avgTone)) {
      throw new IllegalArgumentException("avgTone must be finite");
    }
  }
}
