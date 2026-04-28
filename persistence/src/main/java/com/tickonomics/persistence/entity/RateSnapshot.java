package com.tickonomics.persistence.entity;

import java.time.Instant;

public record RateSnapshot(
        Instant time,
        String rateType,
        double value,
        String source
) {
    public RateSnapshot {
        if (time == null) throw new NullPointerException("time must not be null");
        if (rateType == null || rateType.isBlank()) throw new IllegalArgumentException("rateType must not be blank");
        if (Double.isNaN(value)) throw new IllegalArgumentException("value must not be NaN");
    }
}
