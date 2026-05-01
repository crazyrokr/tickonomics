package com.tickonomics.computation.kpi;

public enum LookbackTier {
  MACRO(252),
  FLOW(60),
  VOLATILITY(20);

  private final int days;

  LookbackTier(int days) {
    this.days = days;
  }

  public int days() {
    return days;
  }
}
