package com.tickonomics.computation.options;

public class CallButterflyStrategy extends BaseOptionStrategy {

    public CallButterflyStrategy() {
        super(StrategyType.CALL_BUTTERFLY);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double lowerWing = midPrice(legs.components().get(0));
        double body = midPrice(legs.components().get(1));
        double upperWing = midPrice(legs.components().get(2));
        double denominator = body == 0.0 ? 1.0 : body;
        return (lowerWing + upperWing - 2.0 * body) / denominator;
    }
}
