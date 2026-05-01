package com.tickonomics.computation.options;

public class CalendarSpreadStrategy extends BaseOptionStrategy {

  public CalendarSpreadStrategy() {
    super(StrategyType.CALENDAR_SPREAD);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    double farMid = midPrice(legs
        .components()
        .get(0));
    double nearMid = midPrice(legs
        .components()
        .get(1));
    return farMid - nearMid;
  }
}
