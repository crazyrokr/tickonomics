package com.tickonomics.computation.kpi;

public record SignalResult(
    String symbol,
    String direction,
    String status,
    double iliPercentile,
    double iliValue,
    double expectedMove,
    double estimatedCost,
    double strength) {
  public static final String STATUS_ACTIONABLE = "ACTIONABLE";
  public static final String STATUS_SPECULATIVE = "SPECULATIVE_STALE_MACRO";
  public static final String STATUS_COST_EXCEEDS = "COST_EXCEEDS_EXPECTED_MOVE";
  public static final String STATUS_COOLDOWN = "COOLDOWN";
  public static final String STATUS_INSUFFICIENT = "INSUFFICIENT_DATA";
  public static final String STATUS_SAFE_MODE = "SAFE_MODE";
  public static final String STATUS_SUSPENDED_UNCERTAINTY = "SUSPENDED_UNCERTAINTY";
  public static final String STATUS_PROCEED_CAUTIOUSLY = "PROCEED_CAUTIOUSLY";

  public static final String DIR_BUY = "BUY";
  public static final String DIR_SELL = "SELL";
}
