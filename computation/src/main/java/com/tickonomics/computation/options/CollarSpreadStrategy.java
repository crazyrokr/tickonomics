package com.tickonomics.computation.options;

public class CollarSpreadStrategy extends BaseOptionStrategy {

    public CollarSpreadStrategy() {
        super(StrategyType.COLLAR_SPREAD);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double callMid = midPrice(legs.components().get(0));
        double putMid = midPrice(legs.components().get(1));
        return callMid - putMid;
    }
}
