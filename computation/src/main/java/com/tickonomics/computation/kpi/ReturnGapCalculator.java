package com.tickonomics.computation.kpi;

import org.springframework.stereotype.Component;

@Component
public class ReturnGapCalculator {

    public ReturnGapResult calculate(double investorGrossReturn, double holdingsReturn) {
        double returnGap = investorGrossReturn - holdingsReturn;
        String interpretation = interpret(returnGap);
        return new ReturnGapResult(returnGap, investorGrossReturn, holdingsReturn, interpretation);
    }

    public ReturnGapResult calculate(double[] investorReturns, double[] holdingsReturns) {
        if (investorReturns == null || holdingsReturns == null ||
                investorReturns.length != holdingsReturns.length || investorReturns.length == 0) {
            return new ReturnGapResult(Double.NaN, Double.NaN, Double.NaN, "INSUFFICIENT_DATA");
        }

        double investorCumulative = cumulativeReturn(investorReturns);
        double holdingsCumulative = cumulativeReturn(holdingsReturns);
        return calculate(investorCumulative, holdingsCumulative);
    }

    double cumulativeReturn(double[] returns) {
        double cumulative = 1.0;
        for (double r : returns) {
            cumulative *= (1.0 + r);
        }
        return cumulative - 1.0;
    }

    String interpret(double returnGap) {
        if (Double.isNaN(returnGap)) {
            return "INSUFFICIENT_DATA";
        }
        if (returnGap > 0.005) {
            return "POSITIVE_EXECUTION_ALPHA";
        }
        if (returnGap < -0.005) {
            return "NEGATIVE_EXECUTION_DRAG";
        }
        return "NEUTRAL";
    }

    public record ReturnGapResult(double returnGap, double investorGrossReturn, double holdingsReturn, String interpretation) {}
}
