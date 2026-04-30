package com.tickonomics.computation.kpi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IliCalculatorTest {

    private IliCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new IliCalculator();
    }

    private ZscoreResult validZ(String component, double zScore) {
        return new ZscoreResult(component, 1.0, zScore, 0.5, 0.2, 60, true);
    }

    private ZscoreResult invalidZ(String component) {
        return ZscoreResult.invalid(component, 1.0, 60);
    }

    @Nested
    class Calculate {
        @Test
        void givenAllValid_whenCalculate_thenValidStatus() {
            var result = calculator.calculate(validZ("Z_RRP", 1.2), validZ("Z_SPREAD", -0.5), validZ("Z_VOL", 0.3));
            assertEquals(IliResult.STATUS_VALID, result.dataStatus());
            double expected = 0.4 * 1.2 + 0.35 * (-0.5) - 0.25 * 0.3;
            assertEquals(expected, result.iliValue(), 1e-9);
        }

        @Test
        void givenCustomWeights_whenCalculate_thenUsesCustomWeights() {
            double[] weights = {0.5, 0.3, 0.2};
            var result = calculator.calculate(validZ("Z_RRP", 1.0), validZ("Z_SPREAD", 1.0), validZ("Z_VOL", 1.0), weights);
            double expected = 0.5 * 1.0 + 0.3 * 1.0 - 0.2 * 1.0;
            assertEquals(expected, result.iliValue(), 1e-9);
        }

        @Test
        void givenOneInvalid_whenCalculate_thenDegradedAndRedistributed() {
            var result = calculator.calculate(validZ("Z_RRP", 1.0), validZ("Z_SPREAD", 1.0), invalidZ("Z_VOL"));
            assertEquals(IliResult.STATUS_DEGRADED, result.dataStatus());
            assertTrue(result.iliValue() != 0.0);
        }

        @Test
        void givenAllInvalid_whenCalculate_thenDegraded() {
            var result = calculator.calculate(invalidZ("Z_RRP"), invalidZ("Z_SPREAD"), invalidZ("Z_VOL"));
            assertEquals(IliResult.STATUS_DEGRADED, result.dataStatus());
            assertEquals(0.0, result.iliValue(), 1e-9);
        }
    }

    @Nested
    class WithProxyDivergence {
        @Test
        void givenDivergent_whenApply_thenDislocatedStatus() {
            var base = calculator.calculate(validZ("Z_RRP", 1.0), validZ("Z_SPREAD", 1.0), validZ("Z_VOL", 1.0));
            var result = calculator.withProxyDivergence(base, true, 3.5);
            assertEquals(IliResult.STATUS_DISLOCATED, result.dataStatus());
            assertEquals("DIVERGENT", result.proxyDivergenceStatus());
            assertEquals(3.5, result.proxyDivergenceScore());
        }

        @Test
        void givenNotDivergent_whenApply_thenPreservedStatus() {
            var base = calculator.calculate(validZ("Z_RRP", 1.0), validZ("Z_SPREAD", 1.0), validZ("Z_VOL", 1.0));
            var result = calculator.withProxyDivergence(base, false, 0.5);
            assertEquals(IliResult.STATUS_VALID, result.dataStatus());
            assertEquals("NORMAL", result.proxyDivergenceStatus());
        }
    }

    @Nested
    class RedistributeWeights {
        @Test
        void givenAllValid_whenRedistribute_thenUnchanged() {
            var result = calculator.redistributeWeights(new double[]{0.4, 0.35, 0.25}, new boolean[]{true, true, true});
            double sum = 0;
            for (double w : result.weights()) {
                sum += w;
            }
            assertEquals(1.0, sum, 1e-9);
        }

        @Test
        void givenOneInvalid_whenRedistribute_thenRedistributedToOthers() {
            var result = calculator.redistributeWeights(
                    new double[]{0.4, 0.35, 0.25}, new boolean[]{true, true, false});
            assertEquals(0.0, result.weights()[2]);
            double sum = result.weights()[0] + result.weights()[1];
            assertEquals(1.0, sum, 1e-9);
            assertTrue(result.weights()[0] > 0.4);
            assertTrue(result.weights()[1] > 0.35);
        }

        @Test
        void givenAllInvalid_whenRedistribute_thenUnchanged() {
            var result = calculator.redistributeWeights(
                    new double[]{0.4, 0.35, 0.25}, new boolean[]{false, false, false});
            assertEquals(0.4, result.weights()[0], 1e-9);
        }

        @Test
        void givenWeightsSumToOne_whenRedistribute_thenStillSumToOne() {
            var result = calculator.redistributeWeights(
                    new double[]{0.5, 0.3, 0.2}, new boolean[]{false, true, true});
            double sum = result.weights()[1] + result.weights()[2];
            assertEquals(1.0, sum, 1e-9);
        }
    }

    @Nested
    class InvalidWeights {
        @Test
        void givenWeightsNotSummingToOne_whenCreateIliResult_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new IliResult(0.5, 1.0, -0.5, 0.3, "VALID", new double[]{0.5, 0.5, 0.5}, null, null));
        }
    }
}
