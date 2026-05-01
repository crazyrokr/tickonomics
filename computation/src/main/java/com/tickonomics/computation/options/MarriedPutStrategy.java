package com.tickonomics.computation.options;

public class MarriedPutStrategy extends BaseOptionStrategy {

  public MarriedPutStrategy() {
    super(StrategyType.MARRIED_PUT);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    return -midPrice(legs
        .components()
        .getFirst());
  }
}
