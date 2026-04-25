package com.tickonomics.computation.options;

public class ProtectivePutStrategy extends BaseOptionStrategy {

    public ProtectivePutStrategy() {
        super(StrategyType.PROTECTIVE_PUT);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        return -midPrice(legs.components().get(0));
    }
}
