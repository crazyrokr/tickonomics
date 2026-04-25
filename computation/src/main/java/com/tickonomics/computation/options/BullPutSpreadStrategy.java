package com.tickonomics.computation.options;

public class BullPutSpreadStrategy extends BaseOptionStrategy {

    public BullPutSpreadStrategy() {
        super(StrategyType.BULL_PUT_SPREAD);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double lowerStrikeMid = midPrice(legs.components().get(0));
        double higherStrikeMid = midPrice(legs.components().get(1));
        return lowerStrikeMid - higherStrikeMid;
    }
}
