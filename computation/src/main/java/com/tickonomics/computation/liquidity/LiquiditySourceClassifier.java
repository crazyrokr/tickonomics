package com.tickonomics.computation.liquidity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LiquiditySourceClassifier {

    private static final Logger log = LoggerFactory.getLogger(LiquiditySourceClassifier.class);

    public record TraderTypeEstimate(double algo, double institutional, double professional,
                                     double retail, String dominantType) {
    }

    public TraderTypeEstimate classify(double avgOrderSize, double orderFrequency,
                                       double cancellationRate, double spreadCrossingRate) {

        double algoScore = orderFrequency * 0.4 + cancellationRate * 0.3 + (1.0 - avgOrderSize) * 0.3;
        double instScore = avgOrderSize * 0.5 + (1.0 - orderFrequency) * 0.3 + (1.0 - spreadCrossingRate) * 0.2;
        double profScore = (1.0 - cancellationRate) * 0.4 + spreadCrossingRate * 0.3 + avgOrderSize * 0.3;
        double retailScore = spreadCrossingRate * 0.5 + (1.0 - avgOrderSize) * 0.3 + (1.0 - orderFrequency) * 0.2;

        double[] raw = {algoScore, instScore, profScore, retailScore};
        double sum = ArraysSum(raw);

        double algoNorm = algoScore / sum;
        double instNorm = instScore / sum;
        double profNorm = profScore / sum;
        double retailNorm = retailScore / sum;

        String dominant = findDominant(algoNorm, instNorm, profNorm, retailNorm);

        log.debug("Trader type estimate: algo={}, inst={}, prof={}, retail={}, dominant={}",
                algoNorm, instNorm, profNorm, retailNorm, dominant);

        return new TraderTypeEstimate(algoNorm, instNorm, profNorm, retailNorm, dominant);
    }

    private double ArraysSum(double[] arr) {
        double s = 0.0;
        for (double v : arr) {
            s += v;
        }
        return s;
    }

    String findDominant(double algo, double institutional, double professional, double retail) {
        double max = Math.max(Math.max(algo, institutional), Math.max(professional, retail));
        if (max == algo) {
            return "ALGO";
        }
        if (max == institutional) {
            return "INSTITUTIONAL";
        }
        if (max == professional) {
            return "PROFESSIONAL";
        }
        return "RETAIL";
    }
}
