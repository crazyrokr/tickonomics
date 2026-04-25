package com.tickonomics.computation.options;

public class LongStraddleStrategy extends BaseOptionStrategy {

    public LongStraddleStrategy() {
        super(StrategyType.LONG_STRADDLE);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double callImplVol = legs.components().get(0).impliedVol();
        double putImplVol = legs.components().get(1).impliedVol();
        return (callImplVol + putImplVol) / 2.0;
    }
}
