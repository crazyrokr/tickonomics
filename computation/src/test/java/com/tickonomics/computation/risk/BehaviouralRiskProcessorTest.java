package com.tickonomics.computation.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BehaviouralRiskProcessorTest {

    private BehaviouralRiskProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new BehaviouralRiskProcessor();
    }

    @Nested
    class BriScoreComputation {

        @Test
        void givenAllZeroInputs_whenCompute_thenZeroBriLowRisk() {
            /*
             * Given: all component scores are 0
             * When: compute() is called
             * Then: briScore=0.0, riskLevel="LOW"
             */
            var result = processor.compute(0.0, 0.0, 0.0, 0.0, 0.0);

            assertEquals(0.0, result.briScore(), 1e-10);
            assertEquals("LOW", result.riskLevel());
        }

        @Test
        void givenAllMaxInputs_whenCompute_thenHighBri() {
            /*
             * Given: all component scores are 1.0
             * When: compute() is called
             * Then: briScore=1.0 (weighted sum of 1s), riskLevel="HIGH"
             */
            var result = processor.compute(1.0, 1.0, 1.0, 1.0, 1.0);

            assertEquals(1.0, result.briScore(), 1e-10);
            assertEquals("HIGH", result.riskLevel());
        }

        @Test
        void givenWeightedComponents_whenCompute_thenCorrectBri() {
            /*
             * Given: ofi=1.0, spreadVol=0.0, sentiment=0.0, cancel=0.0, systemic=0.0
             * When: compute() is called
             * Then: briScore = 0.25*1.0 = 0.25
             */
            var result = processor.compute(1.0, 0.0, 0.0, 0.0, 0.0);

            assertEquals(0.25, result.briScore(), 1e-10);
        }

        @Test
        void givenAllWeightsApplied_whenCompute_thenBriEqualsWeightedSum() {
            /*
             * Given: ofi=0.4, spreadVol=0.6, sentiment=0.8, cancel=0.2, systemic=0.5
             * When: compute() is called
             * Then: briScore = 0.25*0.4 + 0.25*0.6 + 0.20*0.8 + 0.15*0.2 + 0.15*0.5 = 0.495
             */
            var result = processor.compute(0.4, 0.6, 0.8, 0.2, 0.5);

            double expected = 0.25 * 0.4 + 0.25 * 0.6 + 0.20 * 0.8 + 0.15 * 0.2 + 0.15 * 0.5;
            assertEquals(expected, result.briScore(), 1e-10);
        }
    }

    @Nested
    class RiskLevelClassification {

        @Test
        void givenBriBelow30_whenCompute_thenLowRisk() {
            /*
             * Given: inputs producing bri < 0.3
             * When: compute() is called
             * Then: riskLevel = "LOW"
             */
            var result = processor.compute(0.2, 0.1, 0.1, 0.1, 0.1);

            assertTrue(result.briScore() < 0.3);
            assertEquals("LOW", result.riskLevel());
        }

        @Test
        void givenBriAt30_whenCompute_thenMediumRisk() {
            /*
             * Given: inputs producing bri exactly at 0.3
             * When: compute() is called
             * Then: riskLevel = "MEDIUM"
             */
            var result = processor.compute(0.3, 0.3, 0.3, 0.3, 0.3);

            assertEquals(0.3, result.briScore(), 1e-10);
            assertEquals("MEDIUM", result.riskLevel());
        }

        @Test
        void givenBriAt70_whenCompute_thenMediumRisk() {
            /*
             * Given: inputs producing bri exactly at 0.7
             * When: compute() is called
             * Then: riskLevel = "MEDIUM" (boundary is inclusive for MEDIUM)
             */
            var result = processor.compute(0.7, 0.7, 0.7, 0.7, 0.7);

            assertEquals(0.7, result.briScore(), 1e-10);
            assertEquals("MEDIUM", result.riskLevel());
        }

        @Test
        void givenBriAbove70_whenCompute_thenHighRisk() {
            /*
             * Given: inputs producing bri > 0.7
             * When: compute() is called
             * Then: riskLevel = "HIGH"
             */
            var result = processor.compute(1.0, 0.8, 0.9, 0.7, 0.8);

            assertTrue(result.briScore() > 0.7);
            assertEquals("HIGH", result.riskLevel());
        }
    }

    @Nested
    class InputClamping {

        @Test
        void givenNegativeInput_whenCompute_thenClampedToZero() {
            /*
             * Given: a negative component score
             * When: compute() is called
             * Then: the component is clamped to 0.0 before weighting
             */
            var result = processor.compute(-1.0, 0.0, 0.0, 0.0, 0.0);

            assertEquals(0.0, result.briScore(), 1e-10);
        }

        @Test
        void givenInputAboveOne_whenCompute_thenClampedToOne() {
            /*
             * Given: a component score above 1.0
             * When: compute() is called
             * Then: the component is clamped to 1.0 before weighting
             */
            var result = processor.compute(5.0, 0.0, 0.0, 0.0, 0.0);

            assertEquals(0.25, result.briScore(), 1e-10);
        }
    }

    @Nested
    class ResultFields {

        @Test
        void givenAllInputs_whenCompute_thenAllFieldsPopulated() {
            /*
             * Given: valid inputs
             * When: compute() is called
             * Then: all fields in BriResult reflect the inputs accurately
             */
            var result = processor.compute(0.5, 0.6, 0.7, 0.3, 0.4);

            assertEquals(0.5, result.ofiZscore(), 1e-10);
            assertEquals(0.6, result.spreadVolatility(), 1e-10);
            assertEquals(0.7, result.sentimentPolarity(), 1e-10);
            assertEquals(0.3, result.cancellationRatio(), 1e-10);
            assertEquals(0.4, result.systemicRiskContribution(), 1e-10);
            assertNotNull(result.riskLevel());
        }
    }
}
