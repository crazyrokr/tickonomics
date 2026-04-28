package com.tickonomics.ingestion.quality;

import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.repository.ProxyDivergenceEventRepository;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProxyDivergenceGuardTest {

    private static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z");

    @Mock private RateSnapshotRepository rateRepository;
    @Mock private ProxyDivergenceEventRepository divergenceRepository;

    private ProxyDivergenceGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ProxyDivergenceGuard(rateRepository, divergenceRepository);
    }

    @Nested
    class ComputeCorrelation {
        @Test
        void givenIdenticalSeries_whenCompute_thenOne() {
            var series = List.of(
                    new RateSnapshot(NOW, "TBILL_3M", 4.25, "NY_FED"),
                    new RateSnapshot(NOW.plusSeconds(3600), "TBILL_3M", 4.26, "NY_FED"));
            double corr = guard.computeCorrelation(series, series);
            assertEquals(1.0, corr, 1e-9);
        }

        @Test
        void givenSinglePoint_whenCompute_thenOne() {
            var series = List.of(new RateSnapshot(NOW, "TBILL_3M", 4.25, "NY_FED"));
            double corr = guard.computeCorrelation(series, series);
            assertEquals(1.0, corr, 1e-9);
        }
    }

    @Nested
    class ComputeDivergenceScore {
        @Test
        void givenConstantDifference_whenCompute_thenZero() {
            var x = List.of(
                    new RateSnapshot(NOW.minusSeconds(7200), "TBILL_3M", 4.250, "NY_FED"),
                    new RateSnapshot(NOW.minusSeconds(3600), "TBILL_3M", 4.250, "NY_FED"),
                    new RateSnapshot(NOW, "TBILL_3M", 4.250, "NY_FED"));
            var y = List.of(
                    new RateSnapshot(NOW.minusSeconds(7200), "SOFR", 4.350, "NY_FED"),
                    new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.350, "NY_FED"),
                    new RateSnapshot(NOW, "SOFR", 4.350, "NY_FED"));
            double score = guard.computeDivergenceScore(x, y, 1.0);
            assertEquals(0.0, score, 1e-9);
        }

        @Test
        void givenEmptyInput_whenCompute_thenZero() {
            assertEquals(0.0, guard.computeDivergenceScore(Collections.emptyList(), Collections.emptyList(), 1.0));
        }
    }
}
