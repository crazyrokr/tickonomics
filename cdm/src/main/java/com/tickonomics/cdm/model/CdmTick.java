package com.tickonomics.cdm.model;

import java.time.Instant;
import java.util.Objects;

public record CdmTick(
        Instant time,
        String symbol,
        double price,
        long volume,
        int[] conditions
) {
    public CdmTick {
        Objects.requireNonNull(time, "time must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");

        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must be non-negative");
        }
    }
}
