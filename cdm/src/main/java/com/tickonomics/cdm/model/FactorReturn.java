package com.tickonomics.cdm.model;

import com.tickonomics.cdm.enums.FactorSet;

import java.time.Instant;
import java.util.Objects;

/**
 * CDM record for a single row of Fama-French factor returns from the Ken French Data Library.
 * Nullable double fields use NaN to indicate missing values (stored as NULL in TimescaleDB).
 */
public record FactorReturn(
    Instant time, FactorSet factorSet, String frequency, String region,
    double rmRf, double smb, double hml, double rmw, double cma,
    double rf, double mom, double stRev, double ltRev) {
  public FactorReturn {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(factorSet, "factorSet must not be null");
    Objects.requireNonNull(frequency, "frequency must not be null");
    Objects.requireNonNull(region, "region must not be null");
  }

  /**
   * Returns true if the value represents a missing observation in the Ken French CSV data
   * (coded as -99.99 or -999).
   */
  public static boolean isMissing(double value) {
    return Double.isNaN(value) || value == -99.99 || value == -999.0;
  }
}
