package com.tickonomics.computation.kpi;

import java.time.Instant;

public record RegimeResult(
        RegimeType regime,
        double confidence,
        String method,
        Instant detectedAt,
        String description
) {
    public static RegimeResult unknown(String method) {
        return new RegimeResult(RegimeType.NORMAL, 0.0, method, Instant.now(), "Insufficient data for regime detection");
    }
}
