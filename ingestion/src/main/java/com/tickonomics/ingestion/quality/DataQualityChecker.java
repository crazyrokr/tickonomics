package com.tickonomics.ingestion.quality;

import com.tickonomics.persistence.entity.RateSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class DataQualityChecker {

    private static final double OUTLIER_BPS_THRESHOLD = 50.0;
    private static final double BPS_MULTIPLIER = 10000.0;

    public DataQualityResult checkRate(RateSnapshot current, RateSnapshot previous) {
        if (current == null) {
            return DataQualityResult.MISSING;
        }

        if (previous != null) {
            double changeBps = Math.abs(current.value() - previous.value()) * BPS_MULTIPLIER;
            if (changeBps > OUTLIER_BPS_THRESHOLD) {
                return DataQualityResult.OUTLIER;
            }
        }

        return DataQualityResult.VALID;
    }

    public boolean isStale(Instant lastUpdate, Duration maxAge) {
        return lastUpdate != null && Instant.now().isAfter(lastUpdate.plus(maxAge));
    }

    public DataQualityResult checkBatch(List<RateSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return DataQualityResult.MISSING;
        }

        for (int i = 1; i < snapshots.size(); i++) {
            double changeBps = Math.abs(snapshots.get(i).value() - snapshots.get(i - 1).value()) * BPS_MULTIPLIER;
            if (changeBps > OUTLIER_BPS_THRESHOLD) {
                return DataQualityResult.OUTLIER;
            }
        }

        return DataQualityResult.VALID;
    }

    public enum DataQualityResult {
        VALID,
        MISSING,
        OUTLIER,
        STALE
    }
}
