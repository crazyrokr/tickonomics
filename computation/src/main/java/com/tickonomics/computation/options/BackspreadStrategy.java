package com.tickonomics.computation.options;

public class BackspreadStrategy extends BaseOptionStrategy {

  public BackspreadStrategy() {
    super(StrategyType.BACKSPREAD);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    double longMid = midPrice(legs
        .components()
        .get(0));
    double shortMid = midPrice(legs
        .components()
        .get(1));
    return longMid - shortMid;
  }
}
