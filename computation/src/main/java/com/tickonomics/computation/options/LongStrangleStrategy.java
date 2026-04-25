package com.tickonomics.computation.options;

public class LongStrangleStrategy extends BaseOptionStrategy {

    public LongStrangleStrategy() {
        super(StrategyType.LONG_STRANGLE);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double totalCost = midPrice(legs.components().get(0)) + midPrice(legs.components().get(1));
        double underlyingPrice = legs.components().get(0).strike().doubleValue();
        double denominator = underlyingPrice == 0.0 ? 1.0 : underlyingPrice;
        return -totalCost / denominator;
    }
}
