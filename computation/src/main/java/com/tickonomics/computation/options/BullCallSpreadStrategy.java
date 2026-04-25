package com.tickonomics.computation.options;

public class BullCallSpreadStrategy extends BaseOptionStrategy {

    public BullCallSpreadStrategy() {
        super(StrategyType.BULL_CALL_SPREAD);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double lowerStrikeMid = midPrice(legs.components().get(0));
        double upperStrikeMid = midPrice(legs.components().get(1));
        return lowerStrikeMid - upperStrikeMid;
    }
}
