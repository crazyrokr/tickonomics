package com.tickonomics.ingestion.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToxicityMonitor {

    private static final Logger log = LoggerFactory.getLogger(ToxicityMonitor.class);

    public ToxicityResult compute(List<Double> tradeVolumes, List<Double> quoteVolumes, long totalTrades) {
        if (tradeVolumes == null || tradeVolumes.isEmpty()) {
            return new ToxicityResult(0.0, ToxicityClassification.NEUTRAL);
        }

        double orderToTradeRatio = computeOrderToTradeRatio(tradeVolumes, quoteVolumes);
        double volumeConcentration = computeVolumeConcentration(tradeVolumes);

        double toxicityScore = (orderToTradeRatio * 0.6 + volumeConcentration * 0.4) * 100;

        ToxicityClassification classification = classifyToxicity(toxicityScore);
        log.debug("Computed toxicity score={:.2f}, classification={}", toxicityScore, classification);

        return new ToxicityResult(round(toxicityScore), classification);
    }

    double computeOrderToTradeRatio(List<Double> tradeVolumes, List<Double> quoteVolumes) {
        if (quoteVolumes == null || quoteVolumes.isEmpty()) {
            return 0.0;
        }
        double totalQuoteVol = quoteVolumes.stream().mapToDouble(d -> d).sum();
        double totalTradeVol = tradeVolumes.stream().mapToDouble(d -> d).sum();

        if (totalTradeVol == 0) {
            return 0.0;
        }
        return Math.min(totalQuoteVol / totalTradeVol, 5.0) / 5.0;
    }

    double computeVolumeConcentration(List<Double> volumes) {
        if (volumes.size() < 3) {
            return 0.0;
        }
        double mean = volumes.stream().mapToDouble(d -> d).average().orElse(0.0);
        if (mean == 0) {
            return 0.0;
        }

        double maxDeviation = volumes.stream()
                .mapToDouble(d -> Math.abs(d - mean) / mean)
                .max()
                .orElse(0.0);

        return Math.min(maxDeviation, 3.0) / 3.0;
    }

    ToxicityClassification classifyToxicity(double score) {
        if (score > 70) {
            return ToxicityClassification.HARMFUL;
        }
        if (score < 30) {
            return ToxicityClassification.BENEFICIAL;
        }
        return ToxicityClassification.NEUTRAL;
    }

    double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record ToxicityResult(double score, ToxicityClassification classification) {}

    public enum ToxicityClassification {
        HARMFUL, BENEFICIAL, NEUTRAL
    }
}
