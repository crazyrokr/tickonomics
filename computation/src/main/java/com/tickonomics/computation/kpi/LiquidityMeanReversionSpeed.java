package com.tickonomics.computation.kpi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LiquidityMeanReversionSpeed {

    private static final Logger log = LoggerFactory.getLogger(LiquidityMeanReversionSpeed.class);

    private static final int MIN_OBSERVATIONS = 30;
    private static final int AUTOCORRELATION_LAG = 20;
    private static final double AUTOCORRELATION_THRESHOLD = 0.5;

    public record MeanReversionResult(double reversionSpeed, double halfLife,
                                      boolean laggedDrainDetected) {
    }

    public MeanReversionResult compute(double[] priceImpactSeries) {
        if (priceImpactSeries == null || priceImpactSeries.length < MIN_OBSERVATIONS) {
            log.debug("Insufficient observations ({}) for mean reversion, returning defaults",
                    priceImpactSeries != null ? priceImpactSeries.length : 0);
            return new MeanReversionResult(0.0, 0.0, false);
        }

        double reversionSpeed = estimateReversionSpeed(priceImpactSeries);
        double halfLife = reversionSpeed > 0 ? Math.log(2) / reversionSpeed : Double.POSITIVE_INFINITY;
        double autocorrelation = computeAutocorrelation(priceImpactSeries, AUTOCORRELATION_LAG);
        boolean laggedDrainDetected = autocorrelation > AUTOCORRELATION_THRESHOLD;

        log.debug("Mean reversion: speed={}, halfLife={}, autocorrelation={}, drain={}",
                reversionSpeed, halfLife, autocorrelation, laggedDrainDetected);

        return new MeanReversionResult(reversionSpeed, halfLife, laggedDrainDetected);
    }

    double estimateReversionSpeed(double[] series) {
        double sumXY = 0.0;
        double sumXX = 0.0;

        for (int i = 1; i < series.length; i++) {
            double y = series[i];
            double x = series[i - 1];
            sumXY += x * y;
            sumXX += x * x;
        }

        if (sumXX == 0.0) {
            return 0.0;
        }

        double phi = sumXY / sumXX;
        return -Math.log(Math.max(Math.abs(phi), 1e-10));
    }

    double computeAutocorrelation(double[] series, int lag) {
        if (series.length <= lag) {
            return 0.0;
        }

        double mean = 0.0;
        for (double v : series) {
            mean += v;
        }
        mean /= series.length;

        double numerator = 0.0;
        double denominator = 0.0;

        for (int i = 0; i < series.length - lag; i++) {
            double diffCurrent = series[i] - mean;
            double diffLagged = series[i + lag] - mean;
            numerator += diffCurrent * diffLagged;
        }

        for (double v : series) {
            double diff = v - mean;
            denominator += diff * diff;
        }

        if (denominator == 0.0) {
            return 0.0;
        }

        return numerator / denominator;
    }
}
