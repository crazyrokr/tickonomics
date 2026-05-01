package com.tickonomics.computation.options;

public class BoxSpreadStrategy extends BaseOptionStrategy {

  private static final double RISK_FREE_RATE = 0.05;

  public BoxSpreadStrategy() {
    super(StrategyType.BOX_SPREAD);
  }

  @Override
  protected double calculateFormula(LegGroup legs) {
    double bullCallDebit = midPrice(legs
        .components()
        .get(0)) - midPrice(legs
        .components()
        .get(1));
    double bearPutDebit = midPrice(legs
        .components()
        .get(2)) - midPrice(legs
        .components()
        .get(3));
    double totalDebit = bullCallDebit + bearPutDebit;
    double strikeWidth = Math.abs(legs
        .components()
        .get(1)
        .strike()
        .doubleValue() - legs
        .components()
        .get(0)
        .strike()
        .doubleValue());
    double ttm = legs
        .components()
        .get(0)
        .ttmYears();
    double theoreticalValue = strikeWidth * Math.exp(-RISK_FREE_RATE * ttm);
    return theoreticalValue - totalDebit;
  }
}
