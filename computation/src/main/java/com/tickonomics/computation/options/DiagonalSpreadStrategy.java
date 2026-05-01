package com.tickonomics.computation.options;

public class DiagonalSpreadStrategy extends BaseOptionStrategy {

  public DiagonalSpreadStrategy() {
    super(StrategyType.DIAGONAL_SPREAD);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    double farMid = midPrice(legs
        .components()
        .get(0));
    double nearMid = midPrice(legs
        .components()
        .get(1));
    double farDelta = Math.abs(legs
        .components()
        .get(0)
        .delta());
    double nearDelta = Math.abs(legs
        .components()
        .get(1)
        .delta());
    double deltaAdjustment = (farDelta + nearDelta) / 2.0;
    double denominator = deltaAdjustment == 0.0 ? 1.0 : deltaAdjustment;
    return (farMid - nearMid) / denominator;
  }
}
