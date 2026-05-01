package com.tickonomics.computation.options;

public class CoveredCallStrategy extends BaseOptionStrategy {

  public CoveredCallStrategy() {
    super(StrategyType.COVERED_CALL);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    return midPrice(legs
        .components()
        .getFirst());
  }
}
