package com.tickonomics.computation.options;

public class DiagonalCallSpreadStrategy extends BaseOptionStrategy {

    public DiagonalCallSpreadStrategy() {
        super(StrategyType.DIAGONAL_CALL_SPREAD);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double netDebit = midPrice(legs.components().get(0)) - midPrice(legs.components().get(1));
        double underlyingPrice = legs.components().get(0).strike().doubleValue();
        double denominator = underlyingPrice == 0.0 ? 1.0 : underlyingPrice;
        return -netDebit / denominator;
    }
}
