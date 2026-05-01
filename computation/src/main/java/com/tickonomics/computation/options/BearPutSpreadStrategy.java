package com.tickonomics.computation.options;

public class BearPutSpreadStrategy extends BaseOptionStrategy {

  public BearPutSpreadStrategy() {
    super(StrategyType.BEAR_PUT_SPREAD);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    double higherStrikeMid = midPrice(legs
        .components()
        .get(0));
    double lowerStrikeMid = midPrice(legs
        .components()
        .get(1));
    return higherStrikeMid - lowerStrikeMid;
  }
}
