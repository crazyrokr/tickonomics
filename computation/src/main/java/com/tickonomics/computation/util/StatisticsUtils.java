package com.tickonomics.computation.util;

import java.util.Arrays;
import java.util.Collection;

/**
 * Single source of truth for the basic descriptive statistics (mean, variance, standard deviation,
 * Sharpe) used across the computation engine. Adopted by the sites that previously each carried
 * their own copy (PairsTradingEngine, RiskPremiumResidualMonitor, CrossModelValidator,
 * WalkForwardValidator, UniverseAggregator, and the array-based calibration/optimization engines).
 *
 * <p>Two variance conventions are provided:
 * <ul>
 *   <li>{@code population*} — divides by {@code n} (what every prior site used; preserved).</li>
 *   <li>{@code sample*} — divides by {@code n-1} (statistically correct for inference; available
 *       for future use, see the P2 ADR).</li>
 * </ul>
 * Empty/null inputs return {@code 0.0} rather than throwing, matching the prior inline behaviour.
 */
public final class StatisticsUtils {

  private StatisticsUtils() {
    throw new UnsupportedOperationException("utility class");
  }

  public static double mean(Collection<Double> values) {
    if (values == null || values.isEmpty()) {
      return 0.0;
    }
    return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  public static double mean(double[] values) {
    if (values == null || values.length == 0) {
      return 0.0;
    }
    return Arrays.stream(values).average().orElse(0.0);
  }

  public static double populationVariance(Collection<Double> values, double mean) {
    if (values == null || values.isEmpty()) {
      return 0.0;
    }
    return values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0.0);
  }

  public static double populationVariance(double[] values, double mean) {
    if (values == null || values.length == 0) {
      return 0.0;
    }
    return Arrays.stream(values).map(v -> (v - mean) * (v - mean)).average().orElse(0.0);
  }

  public static double populationStdDev(Collection<Double> values, double mean) {
    return Math.sqrt(populationVariance(values, mean));
  }

  public static double populationStdDev(Collection<Double> values) {
    return populationStdDev(values, mean(values));
  }

  public static double populationStdDev(double[] values, double mean) {
    return Math.sqrt(populationVariance(values, mean));
  }

  public static double sampleVariance(Collection<Double> values, double mean) {
    if (values == null || values.size() <= 1) {
      return 0.0;
    }
    double sumSq = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
    return sumSq / (values.size() - 1);
  }

  public static double sampleStdDev(Collection<Double> values, double mean) {
    return Math.sqrt(sampleVariance(values, mean));
  }

  /**
   * Annualization factor for daily-return statistics (√252 trading days), used by the Sharpe and
   * volatility calculations that previously each declared their own copy.
   */
  public static final double DAILY_ANNUALIZATION_FACTOR = Math.sqrt(252);

  /**
   * Population Sharpe ratio of a return series against a per-period risk-free rate. Returns
   * {@code 0.0} when the standard deviation is degenerate (below {@code 1e-12}), matching the prior
   * inline convention.
   */
  public static double sharpe(Collection<Double> returns, double riskFreeRate) {
    double m = mean(returns);
    double std = populationStdDev(returns, m);
    if (std < 1e-12) {
      return 0.0;
    }
    return (m - riskFreeRate) / std;
  }
}
