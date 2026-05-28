package com.tickonomics.computation.regime;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Session-aware regime detection. Maps UTC hours to trading sessions and returns
 * volatility multipliers based on the active session or overlap period.
 */
@Service
public class SessionRangeService {

    private static final Logger log = LoggerFactory.getLogger(SessionRangeService.class);

    static final double VOLATILITY_MULTIPLIER_OVERNIGHT = 0.6;
    static final double VOLATILITY_MULTIPLIER_ASIAN = 0.8;
    static final double VOLATILITY_MULTIPLIER_EUROPEAN = 1.0;
    static final double VOLATILITY_MULTIPLIER_US = 1.2;
    static final double VOLATILITY_MULTIPLIER_OVERLAP_EU_US = 1.5;
    static final double VOLATILITY_MULTIPLIER_OVERLAP_AS_EU = 1.3;

    /**
     * Detects the trading session for a given UTC timestamp.
     *
     * @param timestamp the UTC timestamp to classify
     * @return SessionRange with session type, hour boundaries, and volatility multiplier
     * @throws IllegalArgumentException if timestamp is null
     */
    SessionRange detect(Instant timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }

        ZonedDateTime zdt = timestamp.atZone(ZoneOffset.UTC);
        int hour = zdt.getHour();

        boolean asian = hour >= 0 && hour < 9;
        boolean european = hour >= 7 && hour < 16;
        boolean us = hour >= 13 && hour < 22;

        String sessionType;
        int startHour;
        int endHour;
        double volatilityMultiplier;

        if (european && us) {
            sessionType = "OVERLAP_EU_US";
            startHour = 13;
            endHour = 16;
            volatilityMultiplier = VOLATILITY_MULTIPLIER_OVERLAP_EU_US;
        } else if (asian && european) {
            sessionType = "OVERLAP_AS_EU";
            startHour = 7;
            endHour = 9;
            volatilityMultiplier = VOLATILITY_MULTIPLIER_OVERLAP_AS_EU;
        } else if (asian) {
            sessionType = "ASIAN";
            startHour = 0;
            endHour = 9;
            volatilityMultiplier = VOLATILITY_MULTIPLIER_ASIAN;
        } else if (european) {
            sessionType = "EUROPEAN";
            startHour = 7;
            endHour = 16;
            volatilityMultiplier = VOLATILITY_MULTIPLIER_EUROPEAN;
        } else if (us) {
            sessionType = "US";
            startHour = 13;
            endHour = 22;
            volatilityMultiplier = VOLATILITY_MULTIPLIER_US;
        } else {
            sessionType = "OVERNIGHT";
            startHour = 22;
            endHour = 0;
            volatilityMultiplier = VOLATILITY_MULTIPLIER_OVERNIGHT;
        }

        log.debug("Detected session {} for hour {} UTC", sessionType, hour);
        return new SessionRange(sessionType, startHour, endHour, volatilityMultiplier);
    }

    /**
     * Data carrier for session detection results.
     */
    public record SessionRange(
            String sessionType,
            int startHour,
            int endHour,
            double volatilityMultiplier
    ) {}
}
