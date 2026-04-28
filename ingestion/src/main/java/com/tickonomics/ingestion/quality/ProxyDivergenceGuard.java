package com.tickonomics.ingestion.quality;

import com.tickonomics.persistence.entity.ProxyDivergenceEvent;
import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.repository.ProxyDivergenceEventRepository;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ProxyDivergenceGuard {

    private static final Logger log = LoggerFactory.getLogger(ProxyDivergenceGuard.class);
    private static final double DIVERGENCE_THRESHOLD_STD = 2.0;
    private static final double MIN_CORRELATION_THRESHOLD = 0.5;

    private final RateSnapshotRepository rateRepository;
    private final ProxyDivergenceEventRepository divergenceRepository;

    public ProxyDivergenceGuard(RateSnapshotRepository rateRepository,
                                ProxyDivergenceEventRepository divergenceRepository) {
        this.rateRepository = rateRepository;
        this.divergenceRepository = divergenceRepository;
    }

    public DivergenceResult checkDivergence() {
        var now = Instant.now();
        var fiveDaysAgo = now.minus(java.time.Duration.ofDays(5));

        var tbillRates = rateRepository.findByRateTypeAndTimeBetween("TBILL_3M", fiveDaysAgo, now);
        var sofrRates = rateRepository.findByRateTypeAndTimeBetween("SOFR", fiveDaysAgo, now);

        if (tbillRates.isEmpty() || sofrRates.isEmpty()) {
            return new DivergenceResult(false, 0.0, 0.0, "INSUFFICIENT_DATA");
        }

        double correlation = computeCorrelation(tbillRates, sofrRates);
        double divergenceScore = computeDivergenceScore(tbillRates, sofrRates, correlation);

        if (correlation < MIN_CORRELATION_THRESHOLD || divergenceScore > DIVERGENCE_THRESHOLD_STD) {
            double tbillLatest = tbillRates.getLast().value();
            double sofrLatest = sofrRates.getLast().value();

            divergenceRepository.save(new ProxyDivergenceEvent(
                    now, sofrLatest, tbillLatest, correlation, divergenceScore, null, null));

            log.warn("Proxy divergence detected: correlation={}, score={}, tbill={}, sofr={}",
                    correlation, divergenceScore, tbillLatest, sofrLatest);

            return new DivergenceResult(true, divergenceScore, correlation, "DIVERGENT");
        }

        return new DivergenceResult(false, divergenceScore, correlation, "NORMAL");
    }

    double computeCorrelation(List<RateSnapshot> x, List<RateSnapshot> y) {
        int n = Math.min(x.size(), y.size());
        if (n < 2) return 1.0;

        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) {
            sumX += x.get(i).value();
            sumY += y.get(i).value();
        }
        double meanX = sumX / n;
        double meanY = sumY / n;

        double covXY = 0, varX = 0, varY = 0;
        for (int i = 0; i < n; i++) {
            double dx = x.get(i).value() - meanX;
            double dy = y.get(i).value() - meanY;
            covXY += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }

        if (varX == 0 || varY == 0) return 1.0;
        return covXY / Math.sqrt(varX * varY);
    }

    double computeDivergenceScore(List<RateSnapshot> x, List<RateSnapshot> y, double correlation) {
        if (x.isEmpty() || y.isEmpty()) return 0.0;
        double latestDiff = Math.abs(x.getLast().value() - y.getLast().value());

        double meanDiff = 0;
        int n = Math.min(x.size(), y.size());
        for (int i = 0; i < n; i++) {
            meanDiff += Math.abs(x.get(i).value() - y.get(i).value());
        }
        meanDiff /= n;

        double stdDiff = 0;
        for (int i = 0; i < n; i++) {
            double diff = Math.abs(x.get(i).value() - y.get(i).value()) - meanDiff;
            stdDiff += diff * diff;
        }
        stdDiff = Math.sqrt(stdDiff / n);

        if (stdDiff == 0) return 0.0;
        return (latestDiff - meanDiff) / stdDiff;
    }

    public record DivergenceResult(boolean divergent, double divergenceScore, double correlation, String status) {}
}
