package com.tickonomics.persistence.entity;

import java.time.Instant;

/**
 * Persisted OSINT news event (GDELT 2.0 shape). {@code avgTone} is the news-tone signal compared by
 * the perspective-mismatch engine against prediction-market-implied probability. Backs the
 * {@code news_events} hypertable.
 */
public record NewsEvent(
    Instant time,
    String eventId,
    String source,
    String headline,
    double avgTone,
    String themes,
    String actors,
    String url) {
  public NewsEvent {
    if (time == null) {
      throw new NullPointerException("time must not be null");
    }
    if (eventId == null || eventId.isBlank()) {
      throw new IllegalArgumentException("eventId must not be blank");
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("source must not be blank");
    }
    if (headline == null || headline.isBlank()) {
      throw new IllegalArgumentException("headline must not be blank");
    }
    if (Double.isNaN(avgTone)) {
      throw new IllegalArgumentException("avgTone must not be NaN");
    }
  }
}
