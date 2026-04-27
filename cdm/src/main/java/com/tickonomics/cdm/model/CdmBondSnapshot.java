package com.tickonomics.cdm.model;

import com.tickonomics.cdm.enums.InstrumentType;

import java.time.Instant;
import java.util.Objects;

/**
 * Canonical bond snapshot with analytical Greeks for fixed-income instruments.
 * Used primarily for 3-month T-Bill proxy in ILI computation.
 */
public record CdmBondSnapshot(
        Instant time,
        InstrumentType instrumentType,
        double yieldValue,
        double dv01,
        double convexity,
        double duration,
        double rateDelta,
        double rateGamma,
        String source
) {
    public CdmBondSnapshot {
        Objects.requireNonNull(time, "time must not be null");
        Objects.requireNonNull(instrumentType, "instrumentType must not be null");
        Objects.requireNonNull(source, "source must not be null");

        if (!Double.isFinite(yieldValue)) {
            throw new IllegalArgumentException("yieldValue must be finite");
        }
        if (!Double.isFinite(dv01)) {
            throw new IllegalArgumentException("dv01 must be finite");
        }
        if (!Double.isFinite(convexity)) {
            throw new IllegalArgumentException("convexity must be finite");
        }
        if (!Double.isFinite(duration)) {
            throw new IllegalArgumentException("duration must be finite");
        }
    }
}
