package com.tickonomics.computation.options;

public class SyntheticLongStrategy extends BaseOptionStrategy {

    public SyntheticLongStrategy() {
        super(StrategyType.SYNTHETIC_LONG);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double callMid = midPrice(legs.components().get(0));
        double putMid = midPrice(legs.components().get(1));
        return callMid - putMid;
    }
}
