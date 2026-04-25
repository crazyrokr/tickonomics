package com.tickonomics.computation.options;

public class ButterflyBackspreadStrategy extends BaseOptionStrategy {

    public ButterflyBackspreadStrategy() {
        super(StrategyType.BUTTERFLY_BACKSPREAD);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double wing1Mid = midPrice(legs.components().get(0));
        double body1Mid = midPrice(legs.components().get(1));
        double body2Mid = midPrice(legs.components().get(2));
        double wing2Mid = midPrice(legs.components().get(3));
        double netPremium = wing1Mid + wing2Mid - body1Mid - body2Mid;
        double strikeWidth = Math.abs(
                legs.components().get(3).strike().doubleValue()
                        - legs.components().get(0).strike().doubleValue());
        double maxRisk = strikeWidth - Math.abs(netPremium);
        double denominator = maxRisk == 0.0 ? 1.0 : maxRisk;
        return netPremium / denominator;
    }
}
