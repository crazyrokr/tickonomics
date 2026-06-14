package com.tickonomics.persistence.entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Pairs an ingestion row with the durable idempotency key that originated it, so the writer can
 * upsert with {@code ON CONFLICT (idempotency_key) DO NOTHING} without mutating the row record
 * itself.
 *
 * @param idempotencyKey durable deduplication key (deterministic name-based UUID)
 * @param row            the entity to persist
 * @param <T>            entity type
 */
public record IdempotentRow<T>(UUID idempotencyKey, T row) {

  public IdempotentRow {
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(row, "row must not be null");
  }
}
