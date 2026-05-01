package com.tickonomics.computation.fixedincome;

public enum FixedIncomeStrategyType {
  BULLET_PORTFOLIO("FI.01", "Sec 5 intro"),
  BARBELL_PORTFOLIO("FI.02", "Sec 5 intro"),
  DURATION_NEUTRAL("FI.03", "Eq 374-383");

  private final String strategyId;
  private final String formulaRef;

  FixedIncomeStrategyType(String strategyId, String formulaRef) {
    this.strategyId = strategyId;
    this.formulaRef = formulaRef;
  }

  public String strategyId() {
    return strategyId;
  }

  public String formulaRef() {
    return formulaRef;
  }
}
