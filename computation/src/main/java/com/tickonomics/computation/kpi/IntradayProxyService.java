package com.tickonomics.computation.kpi;

import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import com.tickonomics.persistence.repository.TickDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class IntradayProxyService {

    private static final Logger log = LoggerFactory.getLogger(IntradayProxyService.class);
    private static final double DEFAULT_DIVERGENCE_THRESHOLD_BPS = 50.0;

    private final RateSnapshotRepository rateRepository;
    private final TickDataRepository tickRepository;

    public IntradayProxyService(RateSnapshotRepository rateRepository,
                                TickDataRepository tickRepository) {
        this.rateRepository = rateRepository;
        this.tickRepository = tickRepository;
    }

    public ProxyQualityResult checkProxyQuality(String symbol, String rateType, int windowDays) {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(windowDays));

        List<TickData> ticks = tickRepository.findBySymbolAndTimeBetween(symbol, from, to);
        List<RateSnapshot> rates = rateRepository.findByRateTypeAndTimeBetween(rateType, from, to);

        if (ticks.isEmpty() || rates.isEmpty()) {
            return new ProxyQualityResult(symbol, rateType, 0.0, 0.0, 0,
                    ProxyStatus.INSUFFICIENT_DATA, "Missing tick or rate data");
        }

        double tickMean = ticks.stream().mapToDouble(TickData::price).average().orElse(0.0);
        double rateMean = rates.stream().mapToDouble(RateSnapshot::value).average().orElse(0.0);

        if (tickMean <= 0 || rateMean <= 0) {
            return new ProxyQualityResult(symbol, rateType, 0.0, 0.0, ticks.size(),
                    ProxyStatus.ERROR, "Zero or negative mean values");
        }

        double divergenceBps = Math.abs(tickMean - rateMean) / rateMean * 10000;

        ProxyStatus status;
        String message;
        if (divergenceBps > DEFAULT_DIVERGENCE_THRESHOLD_BPS * 2) {
            status = ProxyStatus.DISLOCATED;
            message = "Proxy divergence exceeds " + (DEFAULT_DIVERGENCE_THRESHOLD_BPS * 2) + " bps";
        } else if (divergenceBps > DEFAULT_DIVERGENCE_THRESHOLD_BPS) {
            status = ProxyStatus.ELEVATED;
            message = "Proxy divergence elevated above " + DEFAULT_DIVERGENCE_THRESHOLD_BPS + " bps";
        } else {
            status = ProxyStatus.NORMAL;
            message = "Proxy within normal bounds";
        }

        return new ProxyQualityResult(symbol, rateType, divergenceBps, rateMean, ticks.size(), status, message);
    }

    public enum ProxyStatus {
        NORMAL, ELEVATED, DISLOCATED, INSUFFICIENT_DATA, ERROR
    }

    public record ProxyQualityResult(
            String symbol,
            String rateType,
            double divergenceBps,
            double referenceRate,
            int tickCount,
            ProxyStatus status,
            String message
    ) {}
}
