package com.tickonomics.computation.options;

public class VerticalPutSpreadStrategy extends BaseOptionStrategy {

  public VerticalPutSpreadStrategy() {
    super(StrategyType.VERTICAL_PUT_SPREAD);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    double lowerStrikeMid = midPrice(legs
        .components()
        .get(0));
    double higherStrikeMid = midPrice(legs
        .components()
        .get(1));
    return lowerStrikeMid - higherStrikeMid;
  }
}
