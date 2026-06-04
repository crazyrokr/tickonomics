package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw news article from free data sources (Finnhub market news, Fed RSS feeds). Provider-agnostic
 * representation used by the sentiment analysis pipeline.
 */
public record NewsArticle(
    Instant time, String title, String summary, String url,
    String source, String sourceType, String category) {
  public NewsArticle {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(sourceType, "sourceType must not be null");
  }
}
