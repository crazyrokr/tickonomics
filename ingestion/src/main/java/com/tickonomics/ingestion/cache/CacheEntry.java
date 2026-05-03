package com.tickonomics.ingestion.cache;

import java.time.Instant;

public record CacheEntry(
    String source,
    Object value,
    Instant cachedAt,
    String stalenessStatus) {

    public static final String FRESH = "FRESH";
    public static final String STALE = "STALE";
    public static final String EXPIRED = "EXPIRED";

    public CacheEntry {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (cachedAt == null) {
            throw new IllegalArgumentException("cachedAt must not be null");
        }
        if (stalenessStatus == null || stalenessStatus.isBlank()) {
            throw new IllegalArgumentException("stalenessStatus must not be blank");
        }
    }
}
