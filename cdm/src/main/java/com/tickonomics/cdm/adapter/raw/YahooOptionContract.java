package com.tickonomics.cdm.adapter.raw;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Raw options contract data from Yahoo Finance v7 options API. Provider-agnostic representation
 * used by CDM adapters.
 */
public record YahooOptionContract(
    Instant time, String underlying, BigDecimal strike, LocalDate expiry,
    String optionType, double bid, double ask, double lastPrice,
    double impliedVol, double delta, double gamma, double theta,
    double vega, double rho, long openInterest, double underlyingPrice) {
  public YahooOptionContract {
    Objects.requireNonNull(time, "time must not be null");
    Objects.requireNonNull(underlying, "underlying must not be null");
    Objects.requireNonNull(strike, "strike must not be null");
    Objects.requireNonNull(expiry, "expiry must not be null");
    Objects.requireNonNull(optionType, "optionType must not be null");
    if (bid < 0) {
      throw new IllegalArgumentException("bid must be non-negative");
    }
    if (ask < 0) {
      throw new IllegalArgumentException("ask must be non-negative");
    }
  }
}
