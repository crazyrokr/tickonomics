package com.tickonomics.computation.signal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TimeOfDayThresholdManager {

    private static final Logger log = LoggerFactory.getLogger(TimeOfDayThresholdManager.class);

    private static final double OPEN_CLOSE_FACTOR = 1.5;
    private static final double MIDDAY_FACTOR = 0.8;
    private static final double DEFAULT_FACTOR = 1.0;

    static final String TIME_OF_DAY_OPEN = "OPEN";
    static final String TIME_OF_DAY_MIDDAY = "MIDDAY";
    static final String TIME_OF_DAY_CLOSE = "CLOSE";
    static final String TIME_OF_DAY_OTHER = "OTHER";

    public record ThresholdAdjustment(double openCloseFactor, double middayFactor,
                                      double currentFactor, String timeOfDay) {
    }

    public ThresholdAdjustment compute(Instant timestamp) {
        ZonedDateTime utc = timestamp.atZone(ZoneOffset.UTC);
        int hour = utc.getHour();
        int minute = utc.getMinute();
        double decimalHour = hour + minute / 60.0;

        String timeOfDay;
        double currentFactor;

        if (isInRange(decimalHour, 13.5, 14.5)) {
            timeOfDay = TIME_OF_DAY_OPEN;
            currentFactor = OPEN_CLOSE_FACTOR;
        } else if (isInRange(decimalHour, 19.0, 20.0)) {
            timeOfDay = TIME_OF_DAY_CLOSE;
            currentFactor = OPEN_CLOSE_FACTOR;
        } else if (isInRange(decimalHour, 15.0, 18.0)) {
            timeOfDay = TIME_OF_DAY_MIDDAY;
            currentFactor = MIDDAY_FACTOR;
        } else {
            timeOfDay = TIME_OF_DAY_OTHER;
            currentFactor = DEFAULT_FACTOR;
        }

        log.debug("Time-of-day adjustment: hour={}, tod={}, factor={}", decimalHour, timeOfDay, currentFactor);

        return new ThresholdAdjustment(OPEN_CLOSE_FACTOR, MIDDAY_FACTOR, currentFactor, timeOfDay);
    }

    private boolean isInRange(double value, double lower, double upper) {
        return value >= lower && value < upper;
    }
}
