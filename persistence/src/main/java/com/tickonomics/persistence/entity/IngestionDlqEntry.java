package com.tickonomics.persistence.entity;

import java.time.Instant;
import java.util.UUID;

public record IngestionDlqEntry(
    Instant createdAt, String source, String payload, String errorMessage, UUID idempotencyKey) {
  public IngestionDlqEntry {
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("source must not be blank");
    }
    if (payload == null || payload.isBlank()) {
      throw new IllegalArgumentException("payload must not be blank");
    }
  }
}
