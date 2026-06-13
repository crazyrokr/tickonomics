package com.tickonomics.ingestion.config;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared fail-fast guards for {@code monitor.*} configuration properties. Centralizes the
 * validation rules called from record compact constructors so each properties type stays terse.
 */
final class MonitorValidation {

  private static final Pattern TICKER = Pattern.compile("^[A-Z]{1,5}$");

  private MonitorValidation() {}

  static void requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive, got " + value);
    }
  }

  static void requirePositive(Duration value, String name) {
    if (value == null || value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(name + " must be a positive duration, got " + value);
    }
  }

  static void validateTickers(List<String> symbols, String name) {
    if (symbols == null || symbols.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    for (String symbol : symbols) {
      if (symbol == null || !TICKER.matcher(symbol).matches()) {
        throw new IllegalArgumentException(
            name + " entries must match [A-Z]{1,5}, got " + symbol);
      }
    }
  }
}
