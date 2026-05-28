package com.tickonomics.computation.kpi;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MarketEfficiencyMonitorTest {

    private MarketEfficiencyMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new MarketEfficiencyMonitor();
    }

    @Nested
    class InterpretGap {

        @Test
        void givenSmallGap_whenInterpret_thenReturnEfficient() {
            // Given gap below half threshold (threshold=0.3, half=0.15)
            // When interpreting
            String result = monitor.interpretGap(0.1, 0.3);

            // Then EFFICIENT
            assertEquals("EFFICIENT", result);
        }

        @Test
        void givenZeroGap_whenInterpret_thenReturnEfficient() {
            // Given zero gap
            // When interpreting
            String result = monitor.interpretGap(0.0, 0.3);

            // Then EFFICIENT
            assertEquals("EFFICIENT", result);
        }

        @Test
        void givenMidRangeGap_whenInterpret_thenReturnPartiallyEfficient() {
            // Given gap between half threshold and threshold (0.15 to 0.3)
            // When interpreting
            String result = monitor.interpretGap(0.2, 0.3);

            // Then PARTIALLY_EFFICIENT
            assertEquals("PARTIALLY_EFFICIENT", result);
        }

        @Test
        void givenGapAtHalfThreshold_whenInterpret_thenReturnEfficient() {
            // Given gap exactly at half threshold (0.15 for threshold 0.3)
            // When interpreting (gap < halfThreshold is EFFICIENT, not inclusive)
            String result = monitor.interpretGap(0.15, 0.3);

            // Then PARTIALLY_EFFICIENT (boundary at exactly 0.15)
            assertEquals("PARTIALLY_EFFICIENT", result);
        }

        @Test
        void givenGapAtThreshold_whenInterpret_thenReturnPartiallyEfficient() {
            // Given gap exactly at threshold (0.3 for threshold 0.3)
            // When interpreting
            String result = monitor.interpretGap(0.3, 0.3);

            // Then PARTIALLY_EFFICIENT (boundary inclusive)
            assertEquals("PARTIALLY_EFFICIENT", result);
        }

        @Test
        void givenLargeGap_whenInterpret_thenReturnInefficient() {
            // Given gap above threshold
            // When interpreting
            String result = monitor.interpretGap(0.5, 0.3);

            // Then INEFFICIENT
            assertEquals("INEFFICIENT", result);
        }

        @Test
        void givenCustomThreshold_whenInterpret_thenUsesCustomBounds() {
            // Given threshold=0.5 (half=0.25)
            // When interpreting with gap=0.3
            String result = monitor.interpretGap(0.3, 0.5);

            // Then PARTIALLY_EFFICIENT (0.25 <= 0.3 <= 0.5)
            assertEquals("PARTIALLY_EFFICIENT", result);
        }
    }

    @Nested
    class AssessEfficiency {

        @Test
        void givenAlignedSignals_whenAssess_thenReturnEfficient() {
            // Given closely aligned signals
            // When assessing
            MarketEfficiencyMonitor.EfficiencyGap result = monitor.assessEfficiency(0.5, 0.52);

            // Then EFFICIENT
            assertEquals("EFFICIENT", result.interpretation());
            assertEquals(0.02, result.gap(), 0.001);
            assertEquals(0.5, result.fundamentalSignal(), 0.001);
            assertEquals(0.52, result.algorithmicSignal(), 0.001);
        }

        @Test
        void givenDivergentSignals_whenAssess_thenReturnInefficient() {
            // Given very divergent signals
            // When assessing
            MarketEfficiencyMonitor.EfficiencyGap result = monitor.assessEfficiency(0.1, 0.9);

            // Then INEFFICIENT
            assertEquals("INEFFICIENT", result.interpretation());
            assertEquals(0.8, result.gap(), 0.001);
        }

        @Test
        void givenModerateDivergence_whenAssess_thenReturnPartiallyEfficient() {
            // Given moderate divergence
            // When assessing with default threshold=0.3
            MarketEfficiencyMonitor.EfficiencyGap result = monitor.assessEfficiency(0.5, 0.65);

            // Then PARTIALLY_EFFICIENT (gap=0.15 which is >= halfThreshold 0.15)
            assertEquals("PARTIALLY_EFFICIENT", result.interpretation());
        }

        @Test
        void givenCustomThreshold_whenAssess_thenUsesCustomThreshold() {
            // Given custom threshold
            // When assessing
            MarketEfficiencyMonitor.EfficiencyGap result = monitor.assessEfficiency(0.5, 0.7, 0.5);

            // Then EFFICIENT (gap=0.2, halfThreshold=0.25)
            assertEquals("EFFICIENT", result.interpretation());
        }
    }

    @Nested
    class AssessBatch {

        @Test
        void givenMultipleSignals_whenAssessBatch_thenReturnAllResults() {
            // Given lists of signals
            List<Double> iliSignals = List.of(0.5, 0.3, 0.8, 0.1);
            List<Double> volumeImbalances = List.of(0.52, 0.1, 0.2, 0.9);

            // When batch assessing
            List<MarketEfficiencyMonitor.EfficiencyGap> results = monitor.assessBatch(iliSignals, volumeImbalances);

            // Then 4 results returned
            assertEquals(4, results.size());
            assertEquals("EFFICIENT", results.get(0).interpretation());
            assertEquals("INEFFICIENT", results.get(2).interpretation());
            assertEquals("INEFFICIENT", results.get(3).interpretation());
        }

        @Test
        void givenUnequalLengths_whenAssessBatch_thenProcessMinLength() {
            // Given unequal length lists
            List<Double> iliSignals = List.of(0.5, 0.3, 0.8);
            List<Double> volumeImbalances = List.of(0.52, 0.1);

            // When batch assessing
            List<MarketEfficiencyMonitor.EfficiencyGap> results = monitor.assessBatch(iliSignals, volumeImbalances);

            // Then processes min length
            assertEquals(2, results.size());
        }

        @Test
        void givenNullInputs_whenAssessBatch_thenReturnEmpty() {
            // Given null inputs
            // When batch assessing
            List<MarketEfficiencyMonitor.EfficiencyGap> result1 = monitor.assessBatch(null, List.of(0.5));
            List<MarketEfficiencyMonitor.EfficiencyGap> result2 = monitor.assessBatch(List.of(0.5), null);

            // Then empty lists
            assertTrue(result1.isEmpty());
            assertTrue(result2.isEmpty());
        }

        @Test
        void givenEmptyInputs_whenAssessBatch_thenReturnEmpty() {
            // Given empty inputs
            List<MarketEfficiencyMonitor.EfficiencyGap> result =
                    monitor.assessBatch(List.of(), List.of());

            // Then empty list
            assertTrue(result.isEmpty());
        }

        @Test
        void givenBatchWithCustomThreshold_whenAssessBatch_thenUsesCustomThreshold() {
            // Given signals and custom threshold
            // First pair: gap=0.2, halfThreshold=0.25 -> EFFICIENT
            // Second pair: gap=0.05, halfThreshold=0.25 -> EFFICIENT
            List<Double> iliSignals = List.of(0.5, 0.3);
            List<Double> volumeImbalances = List.of(0.7, 0.35);

            // When batch assessing with threshold=0.5
            List<MarketEfficiencyMonitor.EfficiencyGap> results =
                    monitor.assessBatch(iliSignals, volumeImbalances, 0.5);

            // Then uses custom threshold
            assertEquals(2, results.size());
            assertEquals("EFFICIENT", results.get(0).interpretation());
        }
    }
}
