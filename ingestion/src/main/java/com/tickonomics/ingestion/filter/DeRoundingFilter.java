package com.tickonomics.ingestion.filter;

import com.tickonomics.persistence.entity.TickData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DeRoundingFilter implements TickFilter {

    private final int windowSeconds;
    private final int roundMarkIntervalMinutes;

    public DeRoundingFilter(
            @Value("${monitor.ingestion.derounding.window-seconds:30}") int windowSeconds,
            @Value("${monitor.ingestion.derounding.round-mark-minutes:5}") int roundMarkIntervalMinutes) {
        this.windowSeconds = windowSeconds;
        this.roundMarkIntervalMinutes = roundMarkIntervalMinutes;
    }

    @Override
    public TickData apply(TickData tick, List<TickData> recentTicks) {
        if (!isNearRoundMark(tick.time())) {
            return tick;
        }

        if (recentTicks.size() < 2) {
            return tick;
        }

        long avgVolume = computeAverageVolume(recentTicks);
        if (!isVolumeSpike(tick.volume(), avgVolume)) {
            return tick;
        }

        double timeWeightedPrice = computeTimeWeightedPrice(recentTicks, tick.time());
        return new TickData(tick.time(), tick.symbol(), timeWeightedPrice, avgVolume, tick.conditions());
    }

    boolean isNearRoundMark(Instant time) {
        long epochSeconds = time.getEpochSecond();
        long secondsInRoundMark = roundMarkIntervalMinutes * 60L;
        long remainder = epochSeconds % secondsInRoundMark;
        return remainder <= windowSeconds || remainder >= secondsInRoundMark - windowSeconds;
    }

    boolean isVolumeSpike(long tickVolume, long averageVolume) {
        if (averageVolume == 0) {
            return false;
        }
        double ratio = (double) tickVolume / averageVolume;
        return ratio > 2.0;
    }

    long computeAverageVolume(List<TickData> ticks) {
        return (long) ticks.stream()
                .mapToLong(TickData::volume)
                .average()
                .orElse(0);
    }

    double computeTimeWeightedPrice(List<TickData> ticks, Instant referenceTime) {
        double weightedSum = 0;
        double weightTotal = 0;

        for (TickData t : ticks) {
            double weight = 1.0 / (1.0 + Math.abs(t.time().toEpochMilli() - referenceTime.toEpochMilli()) / 1000.0);
            weightedSum += t.price() * weight;
            weightTotal += weight;
        }

        return weightTotal > 0 ? weightedSum / weightTotal : ticks.get(ticks.size() - 1).price();
    }
}
