package com.tickonomics.computation.pairs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PairsTradingEngine {

    private static final Logger log = LoggerFactory.getLogger(PairsTradingEngine.class);

    public enum PairsSignalType {
        OPEN_SHORT_A_LONG_B, OPEN_LONG_A_SHORT_B, CLOSE, HOLD
    }

    public record PairsSignal(String symbolA, String symbolB, double distance,
                              double threshold, double spread, PairsSignalType signal) {
    }

    public PairsSignal evaluate(String symbolA, String symbolB,
                                List<Double> seriesA, List<Double> seriesB,
                                double entryThreshold, double exitThreshold) {
        if (seriesA == null || seriesB == null || seriesA.size() != seriesB.size() || seriesA.isEmpty()) {
            log.warn("Invalid input for pairs: seriesA={}, seriesB={}",
                    seriesA != null ? seriesA.size() : 0,
                    seriesB != null ? seriesB.size() : 0);
            return new PairsSignal(symbolA, symbolB, 0.0, entryThreshold, 0.0, PairsSignalType.HOLD);
        }

        double meanA = mean(seriesA);
        double stdA = stdDev(seriesA, meanA);
        double meanB = mean(seriesB);
        double stdB = stdDev(seriesB, meanB);

        double lastA = seriesA.get(seriesA.size() - 1);
        double lastB = seriesB.get(seriesB.size() - 1);

        double normA = stdA > 0 ? (lastA - meanA) / stdA : 0.0;
        double normB = stdB > 0 ? (lastB - meanB) / stdB : 0.0;

        double spread = normA - normB;
        double distance = Math.abs(spread);

        PairsSignalType signal = determineSignal(spread, distance, entryThreshold, exitThreshold);

        log.debug("Pairs signal: {}/{} spread={:.4f} distance={:.4f} signal={}",
                symbolA, symbolB, spread, distance, signal);

        return new PairsSignal(symbolA, symbolB, distance, entryThreshold, spread, signal);
    }

    PairsSignalType determineSignal(double spread, double distance,
                                    double entryThreshold, double exitThreshold) {
        if (spread > entryThreshold) {
            return PairsSignalType.OPEN_SHORT_A_LONG_B;
        }
        if (spread < -entryThreshold) {
            return PairsSignalType.OPEN_LONG_A_SHORT_B;
        }
        if (distance < exitThreshold) {
            return PairsSignalType.CLOSE;
        }
        return PairsSignalType.HOLD;
    }

    double mean(List<Double> values) {
        return values.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    double stdDev(List<Double> values, double mean) {
        double variance = values.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average().orElse(0.0);
        return Math.sqrt(variance);
    }
}
