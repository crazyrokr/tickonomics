package com.tickonomics.computation.kpi;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketEfficiencyMonitor {

    private static final Logger log = LoggerFactory.getLogger(MarketEfficiencyMonitor.class);
    private static final double DEFAULT_THRESHOLD = 0.3;

    public record EfficiencyGap(
            double fundamentalSignal,
            double algorithmicSignal,
            double gap,
            String interpretation) {}

    public MarketEfficiencyMonitor() {}

    public EfficiencyGap assessEfficiency(double iliSignal, double volumeImbalance) {
        return assessEfficiency(iliSignal, volumeImbalance, DEFAULT_THRESHOLD);
    }

    public EfficiencyGap assessEfficiency(double iliSignal, double volumeImbalance, double threshold) {
        double gap = Math.abs(iliSignal - volumeImbalance);
        String interpretation = interpretGap(gap, threshold);

        log.debug("Efficiency assessment: ILI={}, volumeImbalance={}, gap={:.4f}, interpretation={}",
                iliSignal, volumeImbalance, gap, interpretation);

        return new EfficiencyGap(iliSignal, volumeImbalance, gap, interpretation);
    }

    public List<EfficiencyGap> assessBatch(List<Double> iliSignals, List<Double> volumeImbalances) {
        return assessBatch(iliSignals, volumeImbalances, DEFAULT_THRESHOLD);
    }

    public List<EfficiencyGap> assessBatch(
            List<Double> iliSignals,
            List<Double> volumeImbalances,
            double threshold) {

        if (iliSignals == null || volumeImbalances == null) {
            return List.of();
        }

        int size = Math.min(iliSignals.size(), volumeImbalances.size());
        List<EfficiencyGap> results = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            results.add(assessEfficiency(iliSignals.get(i), volumeImbalances.get(i), threshold));
        }

        long efficientCount = results.stream()
                .filter(g -> "EFFICIENT".equals(g.interpretation()))
                .count();

        log.info("Batch efficiency assessment: total={}, efficient={}, ratio={:.2f}",
                size, efficientCount, size > 0 ? (double) efficientCount / size : 0.0);

        return results;
    }

    String interpretGap(double gap, double threshold) {
        double halfThreshold = threshold * 0.5;

        if (gap < halfThreshold) {
            return "EFFICIENT";
        } else if (gap <= threshold) {
            return "PARTIALLY_EFFICIENT";
        }
        return "INEFFICIENT";
    }
}
