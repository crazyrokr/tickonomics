package com.tickonomics.computation.audit;

public enum CodingRule {
  RAW_FETCH("01", "1.0", 1.0),
  ADAPTER_MAP("02", "1.0", 1.0),
  GAP_FILL_LOCF("03A", "1.0", 0.8),
  GAP_FILL_LINEAR("03B", "1.0", 0.5),
  NORMALIZATION("04", "1.0", 1.0),
  GREEKS_CALC("05", "1.0", 1.0),
  SIGNAL_GENERATION("06", "1.0", 1.0);

  private final String ruleId;
  private final String version;
  private final double defaultIrScore;

  CodingRule(String ruleId, String version, double defaultIrScore) {
    this.ruleId = ruleId;
    this.version = version;
    this.defaultIrScore = defaultIrScore;
  }

  public String ruleId() {
    return ruleId;
  }

  public String version() {
    return version;
  }

  public double defaultIrScore() {
    return defaultIrScore;
  }
}
