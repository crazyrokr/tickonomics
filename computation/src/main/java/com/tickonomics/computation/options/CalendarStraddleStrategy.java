package com.tickonomics.computation.options;

public class CalendarStraddleStrategy extends BaseOptionStrategy {

  public CalendarStraddleStrategy() {
    super(StrategyType.CALENDAR_STRADDLE);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    double farCallMid = midPrice(legs
        .components()
        .get(0));
    double farPutMid = midPrice(legs
        .components()
        .get(1));
    double nearCallMid = midPrice(legs
        .components()
        .get(2));
    double nearPutMid = midPrice(legs
        .components()
        .get(3));
    double farStraddleMid = farCallMid + farPutMid;
    double nearStraddleMid = nearCallMid + nearPutMid;
    return farStraddleMid - nearStraddleMid;
  }
}
