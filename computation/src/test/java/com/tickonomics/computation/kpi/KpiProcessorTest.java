package com.tickonomics.computation.kpi;

import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiProcessorTest {

    @Mock private RateSnapshotRepository rateRepository;

    private KpiProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new KpiProcessor(new NormalizationService(), rateRepository);
    }

    private List<RateSnapshot> generateRates(String type, double base, double step, int count) {
        var now = Instant.now();
        var list = new ArrayList<RateSnapshot>(count);
        for (int i = 0; i < count; i++) {
            list.add(new RateSnapshot(now.minusSeconds((long)(count - i) * 86400), type, base + i * step, "TEST", null, null));
        }
        return list;
    }

    @Nested
    class LiquidityStressIndex {
        @Test
        void givenValidRates_whenCompute_thenResultPresent() {
            when(rateRepository.findByRateTypeAndTimeBetween(eq("RRP"), any(), any()))
                    .thenReturn(generateRates("RRP", 2.0, 0.01, 60));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("SOFR"), any(), any()))
                    .thenReturn(generateRates("SOFR", 4.3, 0.001, 60));

            var result = processor.computeLiquidityStressIndex();
            assertEquals("Liquidity Stress Index", result.name());
            assertFalse(Double.isNaN(result.value()));
        }

        @Test
        void givenNoData_whenCompute_thenUnknown() {
            when(rateRepository.findByRateTypeAndTimeBetween(eq("RRP"), any(), any()))
                    .thenReturn(List.of());

            var result = processor.computeLiquidityStressIndex();
            assertEquals(KpiResult.STATUS_UNKNOWN, result.status());
        }
    }

    @Nested
    class RrpDrainVelocity {
        @Test
        void givenDecliningRates_whenCompute_thenNegativeVelocity() {
            var rates = generateRates("RRP", 2.5, -0.01, 30);
            when(rateRepository.findByRateTypeAndTimeBetween(eq("RRP"), any(), any()))
                    .thenReturn(rates);

            var result = processor.computeRrpDrainVelocity();
            assertEquals("RRP Drain Velocity", result.name());
            assertTrue(result.value() < 0);
        }

        @Test
        void givenInsufficientData_whenCompute_thenUnknown() {
            when(rateRepository.findByRateTypeAndTimeBetween(eq("RRP"), any(), any()))
                    .thenReturn(generateRates("RRP", 2.0, 0.01, 3));

            var result = processor.computeRrpDrainVelocity();
            assertEquals(KpiResult.STATUS_UNKNOWN, result.status());
        }
    }

    @Nested
    class VolatilityRegime {
        @Test
        void givenStableRates_whenCompute_thenLowVol() {
            when(rateRepository.findByRateTypeAndTimeBetween(eq("SOFR"), any(), any()))
                    .thenReturn(generateRates("SOFR", 4.30, 0.0001, 60));

            var result = processor.computeVolatilityRegime();
            assertEquals("Volatility Regime", result.name());
            assertTrue(result.value() < 50);
        }
    }

    @Nested
    class EfficiencyGap {
        @Test
        void givenValidData_whenCompute_thenResultPresent() {
            when(rateRepository.findByRateTypeAndTimeBetween(eq("RRP"), any(), any()))
                    .thenReturn(generateRates("RRP", 2.0, 0.01, 20));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("TGA"), any(), any()))
                    .thenReturn(generateRates("TGA", 0.5, 0.005, 20));

            var result = processor.computeEfficiencyGap();
            assertEquals("Efficiency Gap", result.name());
            assertFalse(Double.isNaN(result.value()));
        }

        @Test
        void givenNoTgaData_whenCompute_thenUnknown() {
            when(rateRepository.findByRateTypeAndTimeBetween(eq("RRP"), any(), any()))
                    .thenReturn(generateRates("RRP", 2.0, 0.01, 20));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("TGA"), any(), any()))
                    .thenReturn(List.of());

            var result = processor.computeEfficiencyGap();
            assertEquals(KpiResult.STATUS_UNKNOWN, result.status());
        }
    }

    @Nested
    class SystemicRiskHeatmap {
        @Test
        void givenNormalMarkets_whenCompute_thenNormalStatus() {
            when(rateRepository.findByRateTypeAndTimeBetween(eq("RRP"), any(), any()))
                    .thenReturn(generateRates("RRP", 2.0, 0.01, 20));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("SOFR"), any(), any()))
                    .thenReturn(generateRates("SOFR", 4.3, 0.001, 20));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("EFFR"), any(), any()))
                    .thenReturn(generateRates("EFFR", 4.33, 0.001, 20));

            var result = processor.computeSystemicRiskHeatmap();
            assertEquals(KpiResult.STATUS_NORMAL, result.status());
        }
    }

    @Nested
    class ComputeAll {
        @Test
        void givenAllDependencies_whenComputeAll_thenSixKpis() {
            when(rateRepository.findByRateTypeAndTimeBetween(anyString(), any(), any()))
                    .thenReturn(generateRates("TEST", 4.0, 0.01, 60));

            var results = processor.computeAll(null);
            assertTrue(results.size() >= 5);
            assertTrue(results.containsKey("liquidityStress"));
            assertTrue(results.containsKey("rrpDrainVelocity"));
            assertTrue(results.containsKey("volatilityRegime"));
            assertTrue(results.containsKey("efficiencyGap"));
            assertTrue(results.containsKey("systemicRiskHeatmap"));
        }
    }

    @Nested
    class HelperMethods {
        @Test
        void givenEmptyList_whenMean_thenZero() {
            assertEquals(0.0, processor.mean(List.of()));
        }

        @Test
        void givenSingleValue_whenComputeReturns_thenEmpty() {
            assertEquals(0, processor.computeReturns(List.of(4.5)).length);
        }

        @Test
        void givenStressedSeries_whenIsStressed_thenTrue() {
            var rates = new ArrayList<Double>();
            for (int i = 0; i < 9; i++) {
                rates.add(4.0);
            }
            rates.add(10.0);
            assertTrue(processor.isStressed(rates));
        }

        @Test
        void givenNormalSeries_whenIsStressed_thenFalse() {
            var rates = new ArrayList<Double>();
            for (int i = 0; i < 10; i++) {
                rates.add(4.0 + i * 0.01);
            }
            assertFalse(processor.isStressed(rates));
        }
    }
}
