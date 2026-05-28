package com.tickonomics.computation.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmicBehaviorAlignment {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmicBehaviorAlignment.class);

    public record AlignmentResult(double timeDecayFactor, double adjustedThreshold,
                                  double minutesToPublication) {
    }

    public AlignmentResult computeTimeDecayAlignment(double baseThreshold, double minutesToPublication) {
        double timeDecayFactor = 1.0 + 0.5 * Math.exp(-minutesToPublication / 60.0);
        double adjustedThreshold = baseThreshold * timeDecayFactor;

        log.debug("Time decay: factor={:.4f}, adjustedThreshold={:.4f}, minutesToPublication={:.1f}",
                timeDecayFactor, adjustedThreshold, minutesToPublication);

        return new AlignmentResult(timeDecayFactor, adjustedThreshold, minutesToPublication);
    }

    public double computeLiquidityAdjustedSlippage(double baseSlippage, double liquidityStressIndex) {
        return baseSlippage * (1.0 + liquidityStressIndex);
    }
}
