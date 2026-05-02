package com.tickonomics.computation.kpi;

import com.tickonomics.computation.talib.TalibAdapter;
import com.tickonomics.persistence.repository.CorrelationOutputRepository;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import com.tickonomics.persistence.repository.TickDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationEngineTest {

    @Mock private CorrelationOutputRepository correlationRepository;
    @Mock private RateSnapshotRepository rateRepository;
    @Mock private TickDataRepository tickRepository;

    private CorrelationEngine engine;

    @BeforeEach
    void setUp() {
        TalibAdapter talibAdapter = new TalibAdapter();
        engine = new CorrelationEngine(talibAdapter, correlationRepository, rateRepository, tickRepository);
    }

    @Nested
    class ComputeRollingCorrelation {
        @Test
        void givenSufficientData_whenCompute_thenValidResult() {
            double[] xData = new double[60];
            double[] yData = new double[60];
            for (int i = 0; i < 60; i++) {
                xData[i] = Math.sin(i * 0.1) + 4.0;
                yData[i] = Math.sin(i * 0.1) * 0.9 + 4.0;
            }
            when(rateRepository.findByRateTypeAndTimeBetween(eq("SOFR"), any(Instant.class), any(Instant.class)))
                    .thenReturn(toRateSnapshots("SOFR", xData));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("EFFR"), any(Instant.class), any(Instant.class)))
                    .thenReturn(toRateSnapshots("EFFR", yData));

            var result = engine.computeRollingCorrelation("SOFR", "EFFR", 20);
            assertTrue(result.valid());
            assertEquals("PEARSON_CORRELATION", result.metric());
            assertTrue(result.latest() > 0.5);
        }

        @Test
        void givenInsufficientData_whenCompute_thenInvalid() {
            when(rateRepository.findByRateTypeAndTimeBetween(anyString(), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of());

            var result = engine.computeRollingCorrelation("SOFR", "EFFR", 20);
            assertFalse(result.valid());
        }
    }

    @Nested
    class ComputeRollingBeta {
        @Test
        void givenIdenticalSeries_whenCompute_thenBetaOne() {
            double[] data = new double[60];
            for (int i = 0; i < 60; i++) {
                data[i] = i * 0.01 + 4.0;
            }
            when(rateRepository.findByRateTypeAndTimeBetween(eq("SOFR"), any(Instant.class), any(Instant.class)))
                    .thenReturn(toRateSnapshots("SOFR", data));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("EFFR"), any(Instant.class), any(Instant.class)))
                    .thenReturn(toRateSnapshots("EFFR", data));

            var result = engine.computeRollingBeta("SOFR", "EFFR", 20);
            assertTrue(result.valid());
            assertEquals("OLS_BETA", result.metric());
            assertEquals(1.0, result.latest(), 1e-6);
        }
    }

    private List<com.tickonomics.persistence.entity.RateSnapshot> toRateSnapshots(String rateType, double[] values) {
        var now = Instant.now();
        var snapshots = new java.util.ArrayList<com.tickonomics.persistence.entity.RateSnapshot>();
        for (int i = 0; i < values.length; i++) {
            snapshots.add(new com.tickonomics.persistence.entity.RateSnapshot(
                    now.minusSeconds((values.length - i) * 86400L), rateType, values[i], "TEST"));
        }
        return snapshots;
    }
}
