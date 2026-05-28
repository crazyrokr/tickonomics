package com.tickonomics.computation.kpi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PriceImpactKpi {

    private static final Logger log = LoggerFactory.getLogger(PriceImpactKpi.class);

    public record PriceImpactResult(double submissionMidpoint, double executionPrice,
                                    double impactBps, double halfSpread) {
    }

    public PriceImpactResult computeImpact(double bid, double ask, double executionPrice, String side) {
        double submissionMidpoint = (bid + ask) / 2.0;
        double halfSpread = (ask - bid) / 2.0;
        double impactBps = computeImpactBps(submissionMidpoint, executionPrice, side);

        log.debug("Price impact: side={}, midpoint={}, exec={}, impactBps={}",
                side, submissionMidpoint, executionPrice, impactBps);

        return new PriceImpactResult(submissionMidpoint, executionPrice, impactBps, halfSpread);
    }

    double computeImpactBps(double midpoint, double executionPrice, String side) {
        if (midpoint == 0.0) {
            return 0.0;
        }

        double impactBps;
        if ("BUY".equals(side)) {
            impactBps = (executionPrice - midpoint) / midpoint * 10000.0;
        } else {
            impactBps = (midpoint - executionPrice) / midpoint * 10000.0;
        }

        return impactBps;
    }
}
