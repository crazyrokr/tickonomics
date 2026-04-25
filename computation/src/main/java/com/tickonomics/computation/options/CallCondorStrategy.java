package com.tickonomics.computation.options;

public class CallCondorStrategy extends BaseOptionStrategy {

    public CallCondorStrategy() {
        super(StrategyType.CALL_CONDOR);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double outerLegsMid = midPrice(legs.components().get(0)) + midPrice(legs.components().get(3));
        double innerLegsMid = midPrice(legs.components().get(1)) + midPrice(legs.components().get(2));
        double maxPremium = innerLegsMid;
        double denominator = maxPremium == 0.0 ? 1.0 : maxPremium;
        return (outerLegsMid - innerLegsMid) / denominator;
    }
}
