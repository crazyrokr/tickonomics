package com.tickonomics.computation.options;

public class RiskReversalStrategy extends BaseOptionStrategy {

    public RiskReversalStrategy() {
        super(StrategyType.RISK_REVERSAL);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double putMid = midPrice(legs.components().get(0));
        double callMid = midPrice(legs.components().get(1));
        return putMid - callMid;
    }
}
