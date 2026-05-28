package com.tickonomics.ingestion.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlgorithmicSanityGuard {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmicSanityGuard.class);

    static final double FLASH_MOVE_THRESHOLD = 0.10;
    static final long FLASH_MOVE_WINDOW_MS = 1000;
    static final double HIGH_MESSAGE_RATE = 10_000.0;
    static final long HIGH_RATE_SUSTAINED_MS = 5000;
    static final long BREACH_RETENTION_MS = 60_000;

    private final Clock clock;
    private final Map<String, PriceTracker> priceTrackers = new ConcurrentHashMap<>();
    private final Map<String, RateTracker> rateTrackers = new ConcurrentHashMap<>();
    private final List<SanityBreach> breaches = new ArrayList<>();

    AlgorithmicSanityGuard() {
        this(Clock.systemUTC());
    }

    AlgorithmicSanityGuard(Clock clock) {
        this.clock = clock;
    }

    Instant now() {
        return Instant.now(clock);
    }

    boolean checkPriceMove(String symbol, double currentPrice, double previousPrice, long deltaMs) {
        if (deltaMs <= 0 || previousPrice <= 0) {
            return false;
        }

        double move = Math.abs(currentPrice - previousPrice) / previousPrice;

        if (move > FLASH_MOVE_THRESHOLD && deltaMs < FLASH_MOVE_WINDOW_MS) {
            SanityBreach breach = new SanityBreach(
                    symbol, BreachType.FLASH_MOVE, move, FLASH_MOVE_THRESHOLD, now());
            recordBreach(breach);
            return true;
        }

        priceTrackers.put(symbol, new PriceTracker(currentPrice, now()));
        return false;
    }

    boolean checkMessageRate(String symbol, int messageCount, long windowMs) {
        if (windowMs <= 0) {
            return false;
        }

        double rate = (double) messageCount / (windowMs / 1000.0);

        if (rate > HIGH_MESSAGE_RATE) {
            RateTracker existing = rateTrackers.get(symbol);
            if (existing != null && existing.sustainedAt() != null) {
                Duration sustained = Duration.between(existing.sustainedAt(), now());
                if (sustained.toMillis() >= HIGH_RATE_SUSTAINED_MS) {
                    SanityBreach breach = new SanityBreach(
                            symbol, BreachType.MESSAGE_RATE_SPIKE, rate,
                            HIGH_MESSAGE_RATE, now());
                    recordBreach(breach);
                    return true;
                }
            } else {
                rateTrackers.put(symbol, new RateTracker(now(), now()));
            }
        } else {
            rateTrackers.remove(symbol);
        }

        return false;
    }

    boolean isManualOversight() {
        Instant cutoff = now().minusMillis(BREACH_RETENTION_MS);
        purgeOldBreaches(cutoff);
        return !breaches.isEmpty();
    }

    Collection<SanityBreach> getRecentBreaches() {
        Instant cutoff = now().minusMillis(BREACH_RETENTION_MS);
        purgeOldBreaches(cutoff);
        return List.copyOf(breaches);
    }

    private void recordBreach(SanityBreach breach) {
        breaches.add(breach);
        log.warn("ALGORITHMIC_SANITY_BREACH symbol={} type={} value={} threshold={}",
                breach.symbol(), breach.breachType(),
                String.format("%.4f", breach.value()),
                String.format("%.4f", breach.threshold()));
    }

    private void purgeOldBreaches(Instant cutoff) {
        breaches.removeIf(b -> b.detectedAt().isBefore(cutoff));
    }

    public enum BreachType {
        FLASH_MOVE, MESSAGE_RATE_SPIKE
    }

    public record SanityBreach(
            String symbol,
            BreachType breachType,
            double value,
            double threshold,
            Instant detectedAt
    ) {
    }

    record PriceTracker(double lastPrice, Instant timestamp) {
    }

    record RateTracker(Instant sustainedAt, Instant lastChecked) {
    }
}
