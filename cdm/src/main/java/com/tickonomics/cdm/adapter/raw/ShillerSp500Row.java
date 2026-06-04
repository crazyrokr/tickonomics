package com.tickonomics.cdm.adapter.raw;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw Shiller S&P 500 data row from DataHub CSV. Contains monthly price, dividend, earnings, CPI,
 * and CAPE ratio data from 1871 to present.
 */
public record ShillerSp500Row(
    Instant time, double price, double dividend, double earnings,
    double cpi, double longInterestRate, double realPrice,
    double realDividend, double realEarnings, double cape) {
  public ShillerSp500Row {
    Objects.requireNonNull(time, "time must not be null");
    if (price <= 0) {
      throw new IllegalArgumentException("price must be positive");
    }
  }
}
