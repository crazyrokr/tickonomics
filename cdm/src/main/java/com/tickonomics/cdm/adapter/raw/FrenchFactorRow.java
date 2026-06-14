package com.tickonomics.cdm.adapter.raw;

import com.tickonomics.cdm.enums.FactorSet;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw factor return row parsed from Ken French CSV files. Contains all possible factor columns;
 * unused columns are NaN for a given factor set.
 */
public record FrenchFactorRow(
    Instant time, FactorSet factorSet, String frequency,
    double rmRf, double smb, double hml, double rmw, double cma,
    double rf, double mom) {
  public FrenchFactorRow {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(factorSet, "factorSet must not be null");
    Objects.requireNonNull(frequency, "frequency must not be null");
  }
}
