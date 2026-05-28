package com.tickonomics.ingestion.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderCancellationMonitor {

    private static final Logger log = LoggerFactory.getLogger(OrderCancellationMonitor.class);

    static final double HIGH_CANCELLATION_RATIO = 0.7;
    static final int MINIMUM_ORDER_SAMPLE = 50;

    private final Map<String, CancellationStats> statsBySymbol = new ConcurrentHashMap<>();

    void recordOrder(String symbol, boolean canceled) {
        CancellationStats stats = statsBySymbol.computeIfAbsent(symbol, k -> new CancellationStats(0, 0));
        CancellationStats updated = canceled
                ? new CancellationStats(stats.totalOrders() + 1, stats.canceledOrders() + 1)
                : new CancellationStats(stats.totalOrders() + 1, stats.canceledOrders());
        statsBySymbol.put(symbol, updated);
    }

    CancellationStats getStats(String symbol) {
        return statsBySymbol.getOrDefault(symbol, new CancellationStats(0, 0));
    }

    boolean isHighCancellationRate(String symbol) {
        CancellationStats stats = getStats(symbol);
        if (stats.totalOrders() < MINIMUM_ORDER_SAMPLE) {
            return false;
        }
        double ratio = (double) stats.canceledOrders() / stats.totalOrders();
        return ratio > HIGH_CANCELLATION_RATIO;
    }

    double getCancellationRatio(String symbol) {
        CancellationStats stats = getStats(symbol);
        if (stats.totalOrders() == 0) {
            return 0.0;
        }
        return (double) stats.canceledOrders() / stats.totalOrders();
    }

    List<String> getHighCancellationSymbols(double threshold) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, CancellationStats> entry : statsBySymbol.entrySet()) {
            CancellationStats stats = entry.getValue();
            if (stats.totalOrders() >= MINIMUM_ORDER_SAMPLE) {
                double ratio = (double) stats.canceledOrders() / stats.totalOrders();
                if (ratio > threshold) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    @Scheduled(fixedRate = 3_600_000)
    void resetHourlyStats() {
        int symbolCount = statsBySymbol.size();
        statsBySymbol.clear();
        log.info("Hourly cancellation stats reset. Cleared {} symbols.", symbolCount);
    }

    Map<String, CancellationStats> getAllStats() {
        return Map.copyOf(statsBySymbol);
    }

    public record CancellationStats(int totalOrders, int canceledOrders) {
    }
}
