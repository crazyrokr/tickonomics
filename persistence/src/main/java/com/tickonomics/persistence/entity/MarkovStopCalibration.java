package com.tickonomics.persistence.entity;

import java.time.Instant;

/**
 * Latest calibrated stop-loss / take-profit thresholds for a symbol, persisted by the Python
 * {@code markov_stop_service} into {@code markov_stop_calibrations}. Thresholds are expressed as
 * positive percentages (e.g. {@code 5.0} == 5%).
 */
public record MarkovStopCalibration(
    Long id,
    String symbol,
    double optimalStopLoss,
    double optimalTakeProfit,
    Double signalDrift,
    Double decayIntensity,
    boolean converged,
    Integer iterations,
    Instant calibratedAt) {

  public MarkovStopCalibration {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
    if (optimalStopLoss <= 0 || optimalStopLoss >= 100) {
      throw new IllegalArgumentException("optimalStopLoss must be between 0 and 100 exclusive");
    }
    if (optimalTakeProfit <= 0) {
      throw new IllegalArgumentException("optimalTakeProfit must be positive");
    }
  }
}
