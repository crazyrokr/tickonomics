package com.tickonomics.ingestion.guard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class AlgorithmicSanityGuardTest {

    private MutableClock mutableClock;
    private AlgorithmicSanityGuard guard;

    @BeforeEach
    void setUp() {
        mutableClock = new MutableClock();
        guard = new AlgorithmicSanityGuard(mutableClock);
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public Instant instant() { return instant; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    @Nested
    class FlashMoveDetection {

        @Test
        void givenGreaterThanTenPercentMoveInUnderOneSecond_whenCheckPriceMove_thenDetectsFlashMove() {
            // Given
            String symbol = "AAPL";
            double previous = 100.0;
            double current = 115.0; // 15% move
            long deltaMs = 500; // half a second

            // When
            boolean result = guard.checkPriceMove(symbol, current, previous, deltaMs);

            // Then
            assertThat(result).isTrue();
            assertThat(guard.getRecentBreaches()).hasSize(1);
            AlgorithmicSanityGuard.SanityBreach breach = guard.getRecentBreaches().iterator().next();
            assertThat(breach.symbol()).isEqualTo("AAPL");
            assertThat(breach.breachType()).isEqualTo(AlgorithmicSanityGuard.BreachType.FLASH_MOVE);
        }

        @Test
        void givenExactlyTenPercentMoveInUnderOneSecond_whenCheckPriceMove_thenNoBreach() {
            // Given
            double previous = 100.0;
            double current = 110.0; // exactly 10%
            long deltaMs = 500;

            // When
            boolean result = guard.checkPriceMove("AAPL", current, previous, deltaMs);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenGreaterThanTenPercentMoveInOverOneSecond_whenCheckPriceMove_thenNoBreach() {
            // Given
            double previous = 100.0;
            double current = 115.0; // 15% move
            long deltaMs = 2000; // 2 seconds, exceeds window

            // When
            boolean result = guard.checkPriceMove("AAPL", current, previous, deltaMs);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenNegativeFlashCrash_whenCheckPriceMove_thenDetectsFlashMove() {
            // Given
            double previous = 100.0;
            double current = 85.0; // -15% move
            long deltaMs = 200;

            // When
            boolean result = guard.checkPriceMove("AAPL", current, previous, deltaMs);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void givenZeroDeltaMs_whenCheckPriceMove_thenNoBreach() {
            // Given
            double previous = 100.0;
            double current = 115.0;
            long deltaMs = 0;

            // When
            boolean result = guard.checkPriceMove("AAPL", current, previous, deltaMs);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenZeroPreviousPrice_whenCheckPriceMove_thenNoBreach() {
            // Given
            double previous = 0.0;
            double current = 115.0;
            long deltaMs = 500;

            // When
            boolean result = guard.checkPriceMove("AAPL", current, previous, deltaMs);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenSmallPriceMove_whenCheckPriceMove_thenNoBreach() {
            // Given
            double previous = 100.0;
            double current = 101.0; // 1% move
            long deltaMs = 100;

            // When
            boolean result = guard.checkPriceMove("AAPL", current, previous, deltaMs);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class MessageRateDetection {

        @Test
        void givenSustainedHighMessageRate_whenCheckMessageRate_thenDetectsSpike() {
            // Given - first detection triggers sustained tracking
            guard.checkMessageRate("AAPL", 15_000, 1000); // 15k/sec, starts sustained tracking

            // Advance clock past the sustained window (5 seconds)
            mutableClock.advanceMillis(6_000);

            // When - second check after sustained period
            boolean result = guard.checkMessageRate("AAPL", 12_000, 1000); // still >10k/sec

            // Then
            assertThat(result).isTrue();
            assertThat(guard.getRecentBreaches()).hasSize(1);
            AlgorithmicSanityGuard.SanityBreach breach = guard.getRecentBreaches().iterator().next();
            assertThat(breach.breachType()).isEqualTo(AlgorithmicSanityGuard.BreachType.MESSAGE_RATE_SPIKE);
        }

        @Test
        void givenRateBelowThreshold_whenCheckMessageRate_thenNoSpike() {
            // Given
            int messageCount = 5_000;
            long windowMs = 1000; // 5k/sec

            // When
            boolean result = guard.checkMessageRate("AAPL", messageCount, windowMs);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenExactlyTenThousandPerSecond_whenCheckMessageRate_thenNoSpike() {
            // Given
            int messageCount = 10_000;
            long windowMs = 1000; // exactly 10k/sec

            // When
            boolean result = guard.checkMessageRate("AAPL", messageCount, windowMs);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenZeroWindowMs_whenCheckMessageRate_thenNoSpike() {
            // Given
            int messageCount = 50_000;
            long windowMs = 0;

            // When
            boolean result = guard.checkMessageRate("AAPL", messageCount, windowMs);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenRateDropsBelowThreshold_whenCheckMessageRate_thenTrackerResets() {
            // Given - first call starts tracking
            guard.checkMessageRate("AAPL", 15_000, 1000);

            // Advance a small amount (not enough for sustained)
            mutableClock.advanceMillis(1_000);

            // When - rate drops below threshold
            guard.checkMessageRate("AAPL", 5_000, 1000);

            // Then - tracker should be reset, so next high rate starts fresh
            boolean result = guard.checkMessageRate("AAPL", 15_000, 1000);
            assertThat(result).isFalse(); // not sustained yet
        }
    }

    @Nested
    class ManualOversightFlag {

        @Test
        void givenNoBreaches_whenIsManualOversight_thenReturnsFalse() {
            // Given - fresh guard with no breaches

            // When
            boolean result = guard.isManualOversight();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenRecentBreach_whenIsManualOversight_thenReturnsTrue() {
            // Given
            guard.checkPriceMove("AAPL", 115.0, 100.0, 500);

            // When
            boolean result = guard.isManualOversight();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void givenMultipleSymbolsWithBreaches_whenGetRecentBreaches_thenAllReported() {
            // Given
            guard.checkPriceMove("AAPL", 115.0, 100.0, 500);
            guard.checkPriceMove("GOOG", 200.0, 170.0, 300);

            // When
            Collection<AlgorithmicSanityGuard.SanityBreach> breaches = guard.getRecentBreaches();

            // Then
            assertThat(breaches).hasSize(2);
            assertThat(breaches.stream().map(AlgorithmicSanityGuard.SanityBreach::symbol))
                    .containsExactlyInAnyOrder("AAPL", "GOOG");
        }
    }

    @Nested
    class NoFalsePositives {

        @Test
        void givenNormalMarketActivity_whenChecksRun_thenNoBreaches() {
            // Given - normal price moves and rates
            boolean priceOk = guard.checkPriceMove("AAPL", 100.5, 100.0, 1000);
            boolean rateOk = guard.checkMessageRate("AAPL", 500, 1000);

            // Then
            assertThat(priceOk).isFalse();
            assertThat(rateOk).isFalse();
            assertThat(guard.isManualOversight()).isFalse();
        }

        @Test
        void givenGradualPriceChange_whenCheckPriceMove_thenNoBreach() {
            // Given - 5% move over 5 seconds
            double previous = 100.0;
            double current = 105.0;
            long deltaMs = 5000;

            // When
            boolean result = guard.checkPriceMove("AAPL", current, previous, deltaMs);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenMultipleSymbolsTracked_whenOneBreaches_thenOthersUnaffected() {
            // Given
            guard.checkPriceMove("AAPL", 100.0, 100.5, 500); // normal

            // When
            boolean breached = guard.checkPriceMove("GOOG", 200.0, 170.0, 300); // flash crash

            // Then
            assertThat(breached).isTrue();
            assertThat(guard.getRecentBreaches()).hasSize(1);
            assertThat(guard.getRecentBreaches().iterator().next().symbol()).isEqualTo("GOOG");
        }
    }
}
