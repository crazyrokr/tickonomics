package com.tickonomics.computation.regime;

import com.tickonomics.computation.kpi.RegimeDetector;
import com.tickonomics.computation.kpi.RegimeResult;
import com.tickonomics.computation.kpi.RegimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "monitor.computation.gex-regime.enabled", havingValue = "true", matchIfMissing = false)
public class GexWeightedRegimeDetector {

    private static final Logger log = LoggerFactory.getLogger(GexWeightedRegimeDetector.class);

    private static final double GEX_VOLATILITY_SPIKE_THRESHOLD = -1_000_000.0;
    private static final double GEX_DAMPENING_THRESHOLD = 1_000_000.0;

    private final RegimeDetector delegate;

    public GexWeightedRegimeDetector(RegimeDetector delegate) {
        this.delegate = delegate;
    }

    public RegimeResult detectWithGex(List<Double> returns, double aggregateGex) {
        RegimeResult base = delegate.detect(returns);

        if (aggregateGex < GEX_VOLATILITY_SPIKE_THRESHOLD) {
            return upgradeRegime(base, RegimeType.HIGH_VOL,
                    "Negative GEX=" + aggregateGex + " indicates short gamma volatility spike risk");
        }

        if (aggregateGex > GEX_DAMPENING_THRESHOLD) {
            return downgradeRegime(base,
                    "Positive GEX=" + aggregateGex + " indicates long gamma volatility dampening");
        }

        return base;
    }

    public double computeAggregateGex(List<GexDataPoint> optionsData) {
        if (optionsData == null || optionsData.isEmpty()) {
            return 0.0;
        }

        double aggregateGex = 0;
        for (GexDataPoint dp : optionsData) {
            aggregateGex += dp.gamma() * dp.openInterest() * dp.contractSize() * dp.spotPrice();
        }

        log.debug("Computed aggregate GEX: {}", aggregateGex);
        return aggregateGex;
    }

    private RegimeResult upgradeRegime(RegimeResult base, RegimeType target, String reason) {
        if (base.regime().ordinal() < target.ordinal()) {
            return new RegimeResult(target, base.confidence(), "GEX_WEIGHTED",
                    base.detectedAt(), reason);
        }
        return base;
    }

    private RegimeResult downgradeRegime(RegimeResult base, String reason) {
        if (base.regime() == RegimeType.HIGH_VOL || base.regime() == RegimeType.UNSTABLE) {
            return new RegimeResult(RegimeType.METASTABLE, base.confidence() * 0.8, "GEX_WEIGHTED",
                    base.detectedAt(), reason);
        }
        return base;
    }

    public record GexDataPoint(double gamma, long openInterest, int contractSize, double spotPrice) {}
}
