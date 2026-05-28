package com.tickonomics.computation.ili;

import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Multiplicative climate-sensitivity factor applied to ILI thresholds.
 * Calls the analytics worker climate-simulation endpoint, caches the factor
 * for 24 hours, and falls back to a neutral factor of 0.0 on any failure.
 */
@Service
public class ClimateSensitivityFactor {

    private static final Logger log = LoggerFactory.getLogger(ClimateSensitivityFactor.class);

    private static final String CLIMATE_ENDPOINT = "/api/v1/climate/simulate";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final double FALLBACK_FACTOR = 0.0;

    private final AnalyticsWorkerClient client;

    private volatile double cachedFactor = FALLBACK_FACTOR;
    private volatile Instant cachedAt = Instant.EPOCH;

    public ClimateSensitivityFactor(AnalyticsWorkerClient client) {
        this.client = client;
    }

    /**
     * Adjusts ILI thresholds using the cached or freshly fetched climate factor.
     *
     * @param rrp    repo rate parameter
     * @param spread bid-ask spread parameter
     * @param vol    volatility parameter
     * @return ThresholdAdjustment containing multiplicative factors per input
     */
    public ThresholdAdjustment adjustThresholds(double rrp, double spread, double vol) {
        double factor = resolveFactor();

        double rrpAdjusted = rrp * (1.0 + factor);
        double spreadAdjusted = spread * (1.0 + factor);
        double volAdjusted = vol * (1.0 + factor);

        log.debug("Climate factor={}, rrp={} -> {}, spread={} -> {}, vol={} -> {}",
                factor, rrp, rrpAdjusted, spread, spreadAdjusted, vol, volAdjusted);

        return new ThresholdAdjustment(factor, rrpAdjusted, spreadAdjusted, volAdjusted);
    }

    private double resolveFactor() {
        Instant now = Instant.now();
        if (Duration.between(cachedAt, now).compareTo(CACHE_TTL) < 0) {
            log.debug("Using cached climate factor={} fetched at={}", cachedFactor, cachedAt);
            return cachedFactor;
        }

        try {
            Map<String, Object> response = client.sendAnalysisRequest(
                    CLIMATE_ENDPOINT, Map.of());
            double fetchedFactor = extractFactor(response);
            cachedFactor = fetchedFactor;
            cachedAt = now;
            log.info("Refreshed climate factor={} at={}", fetchedFactor, now);
            return fetchedFactor;
        } catch (Exception e) {
            log.warn("Climate simulation call failed, using fallback factor={}: {}",
                    FALLBACK_FACTOR, e.getMessage());
            return FALLBACK_FACTOR;
        }
    }

    @SuppressWarnings("unchecked")
    private double extractFactor(Map<String, Object> response) {
        if (response == null || response.containsKey("error")) {
            log.warn("Climate response is null or contains error: {}", response);
            return FALLBACK_FACTOR;
        }

        Object raw = response.getOrDefault("factor", FALLBACK_FACTOR);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (Double.isFinite(value)) {
                return value;
            }
        }
        log.warn("Unparseable climate factor from response: {}", raw);
        return FALLBACK_FACTOR;
    }

    /**
     * Forces a cache expiry on the next call. Visible for testing.
     */
    void invalidateCache() {
        cachedAt = Instant.EPOCH;
    }

    /**
     * Data carrier for threshold adjustment results.
     *
     * @param factor           the resolved climate sensitivity factor
     * @param rrpAdjusted      RRP threshold after applying the factor
     * @param spreadAdjusted   spread threshold after applying the factor
     * @param volAdjusted      volatility threshold after applying the factor
     */
    public record ThresholdAdjustment(
            double factor,
            double rrpAdjusted,
            double spreadAdjusted,
            double volAdjusted
    ) {}
}
