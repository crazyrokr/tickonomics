package com.tickonomics.computation.options;

public class ChristmasTreeStrategy extends BaseOptionStrategy {

  public ChristmasTreeStrategy() {
    super(StrategyType.CHRISTMAS_TREE);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    double k1Mid = midPrice(legs
        .components()
        .get(0));
    double k2Mid = midPrice(legs
        .components()
        .get(1));
    double k3Mid = midPrice(legs
        .components()
        .get(2));
    double k1 = legs
        .components()
        .get(0)
        .strike()
        .doubleValue();
    double k2 = legs
        .components()
        .get(1)
        .strike()
        .doubleValue();
    double k3 = legs
        .components()
        .get(2)
        .strike()
        .doubleValue();
    double skippedStrikeWidth = Math.abs(k3 - k2) - Math.abs(k2 - k1);
    double skewAdjustment = skippedStrikeWidth > 0 ? 1.0 : 0.5;
    double denominator = k2Mid == 0.0 ? 1.0 : k2Mid;
    return (k1Mid + k3Mid - 2.0 * k2Mid) * skewAdjustment / denominator;
  }
}
