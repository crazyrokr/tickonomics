package com.tickonomics.computation.options;

public class StraddleSwapStrategy extends BaseOptionStrategy {

    public StraddleSwapStrategy() {
        super(StrategyType.STRADDLE_SWAP);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double callMid = midPrice(legs.components().get(0));
        double putMid = midPrice(legs.components().get(1));
        return callMid - putMid;
    }
}
