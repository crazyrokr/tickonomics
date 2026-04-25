package com.tickonomics.computation.options;

public class IronCondorStrategy extends BaseOptionStrategy {

    public IronCondorStrategy() {
        super(StrategyType.IRON_CONDOR);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double longPutMid = midPrice(legs.components().get(0));
        double shortPutMid = midPrice(legs.components().get(1));
        double shortCallMid = midPrice(legs.components().get(2));
        double longCallMid = midPrice(legs.components().get(3));
        double netCredit = (shortPutMid - longPutMid) + (shortCallMid - longCallMid);
        double wingWidth = Math.abs(
                legs.components().get(1).strike().doubleValue()
                        - legs.components().get(0).strike().doubleValue());
        double maxRisk = (wingWidth * 100.0) - netCredit;
        double denominator = maxRisk == 0.0 ? 1.0 : maxRisk;
        return netCredit / denominator;
    }
}
