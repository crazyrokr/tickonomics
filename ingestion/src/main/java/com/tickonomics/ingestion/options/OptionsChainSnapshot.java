package com.tickonomics.ingestion.options;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record OptionsChainSnapshot(
    Instant time,
    String underlying,
    BigDecimal strike,
    LocalDate expiry,
    String optionType,
    double bid,
    double ask,
    double lastPrice,
    double impliedVol,
    double delta,
    double gamma,
    double theta,
    double vega,
    double rho,
    long openInterest,
    double underlyingPrice) {

    public OptionsChainSnapshot {
        if (underlying == null || underlying.isBlank()) {
            throw new IllegalArgumentException("underlying must not be blank");
        }
        if (strike == null || strike.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("strike must be positive");
        }
        if (bid < 0 || ask < 0) {
            throw new IllegalArgumentException("bid and ask must not be negative");
        }
    }
}
