package com.tickonomics.computation.demo;

/**
 * Point-in-time cross-module health reading consumed by {@link SystemicResilienceMonitor}.
 *
 * <p>The {@code -1} sentinels ({@link #UNKNOWN_UTILIZATION} / {@link #UNKNOWN_LATENCY}) denote
 * "unknown". An unknown reading is never counted as a degraded indicator on its own, so a
 * partially wired health probe cannot trip Global Safe Mode through missing data alone.
 */
public record ResilienceHealthSnapshot(
    double overflowUtilizationPct,
    long workerLatencyMs,
    boolean workerHealthy,
    boolean proxyDivergenceActive,
    boolean wsHealthy) {

  public static final double UNKNOWN_UTILIZATION = -1.0;
  public static final long UNKNOWN_LATENCY = -1L;

  /** Neutral snapshot with no degraded indicators; used when health data is unavailable. */
  public static ResilienceHealthSnapshot unknown() {
    return new ResilienceHealthSnapshot(UNKNOWN_UTILIZATION, UNKNOWN_LATENCY, true, false, true);
  }
}
