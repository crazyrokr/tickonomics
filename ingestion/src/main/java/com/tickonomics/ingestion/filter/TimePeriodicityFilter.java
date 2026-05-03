package com.tickonomics.ingestion.filter;

import com.tickonomics.persistence.entity.TickData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class TimePeriodicityFilter implements TickFilter {

    private final int spikeWindowMs;

    public TimePeriodicityFilter(
            @Value("${monitor.ingestion.periodicity.spike-window-ms:100}") int spikeWindowMs) {
        this.spikeWindowMs = spikeWindowMs;
    }

    @Override
    public TickData apply(TickData tick, List<TickData> recentTicks) {
        if (!isNearWholeSecond(tick.time())) {
            return tick;
        }

        if (recentTicks.size() < 2) {
            return tick;
        }

        if (!isPriceSpike(tick, recentTicks)) {
            return tick;
        }

        double smoothedPrice = computeSmoothedPrice(recentTicks);
        long smoothedVolume = computeSmoothedVolume(recentTicks);
        return new TickData(tick.time(), tick.symbol(), smoothedPrice, smoothedVolume, tick.conditions());
    }

    boolean isNearWholeSecond(Instant time) {
        long nanos = time.getNano();
        long millisInSecond = nanos / 1_000_000;
        return millisInSecond <= spikeWindowMs || millisInSecond >= 1000 - spikeWindowMs;
    }

    boolean isPriceSpike(TickData tick, List<TickData> recentTicks) {
        double avgPrice = recentTicks.stream()
                .mapToDouble(TickData::price)
                .average()
                .orElse(tick.price());

        if (avgPrice == 0) {
            return false;
        }
        double deviation = Math.abs(tick.price() - avgPrice) / avgPrice;
        return deviation > 0.005;
    }

    double computeSmoothedPrice(List<TickData> ticks) {
        return ticks.stream()
                .mapToDouble(TickData::price)
                .average()
                .orElse(0);
    }

    long computeSmoothedVolume(List<TickData> ticks) {
        return (long) ticks.stream()
                .mapToLong(TickData::volume)
                .average()
                .orElse(0);
    }
}
