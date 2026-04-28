package com.tickonomics.ingestion.quality;

import com.tickonomics.persistence.entity.RateSnapshot;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataQualityCheckerTest {

    private static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z");
    private final DataQualityChecker checker = new DataQualityChecker();

    @Nested
    class CheckRate {
        @Test
        void givenNullCurrent_whenCheck_thenMissing() {
            assertEquals(DataQualityChecker.DataQualityResult.MISSING, checker.checkRate(null, null));
        }

        @Test
        void givenNormalChange_whenCheck_thenValid() {
            var prev = new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.2900, "NY_FED");
            var curr = new RateSnapshot(NOW, "SOFR", 4.2910, "NY_FED");
            assertEquals(DataQualityChecker.DataQualityResult.VALID, checker.checkRate(curr, prev));
        }

        @Test
        void givenLargeChange_whenCheck_thenOutlier() {
            var prev = new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.29, "NY_FED");
            var curr = new RateSnapshot(NOW, "SOFR", 5.00, "NY_FED");
            assertEquals(DataQualityChecker.DataQualityResult.OUTLIER, checker.checkRate(curr, prev));
        }

        @Test
        void givenNoPrevious_whenCheck_thenValid() {
            var curr = new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED");
            assertEquals(DataQualityChecker.DataQualityResult.VALID, checker.checkRate(curr, null));
        }
    }

    @Nested
    class IsStale {
        @Test
        void givenRecentUpdate_whenCheck_thenNotStale() {
            assertFalse(checker.isStale(Instant.now().minusSeconds(60), Duration.ofMinutes(5)));
        }

        @Test
        void givenOldUpdate_whenCheck_thenStale() {
            assertTrue(checker.isStale(Instant.now().minus(Duration.ofMinutes(10)), Duration.ofMinutes(5)));
        }
    }

    @Nested
    class CheckBatch {
        @Test
        void givenNullList_whenCheck_thenMissing() {
            assertEquals(DataQualityChecker.DataQualityResult.MISSING, checker.checkBatch(null));
        }

        @Test
        void givenEmptyList_whenCheck_thenMissing() {
            assertEquals(DataQualityChecker.DataQualityResult.MISSING, checker.checkBatch(Collections.emptyList()));
        }

        @Test
        void givenNormalBatch_whenCheck_thenValid() {
            var batch = List.of(
                    new RateSnapshot(NOW.minusSeconds(7200), "SOFR", 4.2900, "NY_FED"),
                    new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.2905, "NY_FED"),
                    new RateSnapshot(NOW, "SOFR", 4.2910, "NY_FED"));
            assertEquals(DataQualityChecker.DataQualityResult.VALID, checker.checkBatch(batch));
        }

        @Test
        void givenOutlierInBatch_whenCheck_thenOutlier() {
            var batch = List.of(
                    new RateSnapshot(NOW.minusSeconds(7200), "SOFR", 4.28, "NY_FED"),
                    new RateSnapshot(NOW, "SOFR", 5.50, "NY_FED"));
            assertEquals(DataQualityChecker.DataQualityResult.OUTLIER, checker.checkBatch(batch));
        }
    }
}
