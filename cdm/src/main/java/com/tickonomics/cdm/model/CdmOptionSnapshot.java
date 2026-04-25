package com.tickonomics.cdm.model;

import com.tickonomics.cdm.enums.DayCountConvention;
import com.tickonomics.cdm.enums.OptionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CdmOptionSnapshot(
        UUID id,
        String underlyingSymbol,
        BigDecimal strike,
        LocalDate expiryDate,
        OptionType type,
        DayCountConvention dayCountConvention,
        double delta,
        double gamma,
        double theta,
        double vega,
        double rho,
        double impliedVol,
        double ttmYears,
        double bid,
        double ask,
        long openInterest,
        Instant observationTime
) {
    public CdmOptionSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(underlyingSymbol, "underlyingSymbol must not be null");
        Objects.requireNonNull(strike, "strike must not be null");
        Objects.requireNonNull(expiryDate, "expiryDate must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(dayCountConvention, "dayCountConvention must not be null");
        Objects.requireNonNull(observationTime, "observationTime must not be null");

        if (strike.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("strike must be positive");
        }
        if (ttmYears < 0) {
            throw new IllegalArgumentException("ttmYears must be non-negative");
        }
        if (bid < 0) {
            throw new IllegalArgumentException("bid must be non-negative");
        }
        if (ask < 0) {
            throw new IllegalArgumentException("ask must be non-negative");
        }
        if (ask < bid) {
            throw new IllegalArgumentException("ask must be greater than or equal to bid");
        }
    }
}
