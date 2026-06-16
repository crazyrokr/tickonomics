package com.tickonomics.computation.kpi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RegimeDetector {

    private static final Logger log = LoggerFactory.getLogger(RegimeDetector.class);

    private String primaryMethod = "VOLATILITY_PERCENTILE";

    public RegimeResult detect(List<Double> returns) {
        if (returns == null || returns.size() < 20) {
            return RegimeResult.unknown(primaryMethod);
        }

        return detectByVolatilityPercentile(returns);
    }

    public RegimeResult detectByVolatilityPercentile(List<Double> returns) {
        double[] realizedVol = computeRollingVol(returns, 20);

        if (realizedVol.length == 0) {
            return RegimeResult.unknown("VOLATILITY_PERCENTILE");
        }

        double latestVol = realizedVol[realizedVol.length - 1];
        double meanVol = mean(realizedVol);
        double stdVol = std(realizedVol, meanVol);

        RegimeType regime;
        double confidence;

        if (stdVol == 0) {
            regime = RegimeType.NORMAL;
            confidence = 0.5;
        } else {
            double zVol = (latestVol - meanVol) / stdVol;

            if (zVol > 2.0) {
                regime = RegimeType.UNSTABLE;
                confidence = Math.min(0.95, 0.5 + zVol * 0.1);
            } else if (zVol > 1.0) {
                regime = RegimeType.HIGH_VOL;
                confidence = 0.5 + zVol * 0.15;
            } else if (zVol > 0.0) {
                regime = RegimeType.METASTABLE;
                confidence = 0.5 + zVol * 0.2;
            } else if (zVol > -1.0) {
                regime = RegimeType.NORMAL;
                confidence = 0.5 + Math.abs(zVol) * 0.1;
            } else {
                regime = RegimeType.LOW_VOL;
                confidence = Math.min(0.95, 0.5 + Math.abs(zVol) * 0.15);
            }
        }

        log.debug("Detected regime {} with confidence {:.2f} (vol={:.4f}, mean={:.4f})",
                regime, confidence, latestVol, meanVol);

        return new RegimeResult(regime, confidence, "VOLATILITY_PERCENTILE", Instant.now(),
                "Volatility percentile regime: z=" + round(latestVol - meanVol) / (stdVol > 0 ? stdVol : 1));
    }

    public RegimeResult withExogenousShock(RegimeResult current, boolean shockDetected) {
        if (!shockDetected) {
      return current;
    }
        return new RegimeResult(RegimeType.EXOGENOUS_SHOCK, 0.95, "EXOGENOUS_OVERRIDE",
                Instant.now(), "External shock detected - overriding regime to EXOGENOUS_SHOCK");
    }

    double[] computeRollingVol(List<Double> returns, int window) {
        if (returns.size() < window) {
      return new double[0];
    }

        int numWindows = returns.size() - window + 1;
        double[] vols = new double[numWindows];

        for (int i = 0; i < numWindows; i++) {
            double sumSq = 0;
            double winMean = 0;
            for (int j = i; j < i + window; j++) {
                winMean += returns.get(j);
            }
            winMean /= window;
            for (int j = i; j < i + window; j++) {
                sumSq += Math.pow(returns.get(j) - winMean, 2);
            }
            vols[i] = Math.sqrt(sumSq / window) * Math.sqrt(252);
        }

        return vols;
    }

    double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    double std(double[] values, double mean) {
        double sumSq = 0;
        for (double v : values) {
            sumSq += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sumSq / values.length);
    }

    double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
