package com.tickonomics.computation.kpi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegimeDetectorTest {

    private RegimeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new RegimeDetector();
    }

    private List<Double> generateReturns(int count, double mean, double std, long seed) {
        var rng = new Random(seed);
        var list = new ArrayList<Double>(count);
        for (int i = 0; i < count; i++) {
            list.add(mean + rng.nextGaussian() * std);
        }
        return list;
    }

    @Nested
    class Detect {
        @Test
        void givenLowVolatility_whenDetect_thenLowVol() {
            var returns = generateReturns(100, 0.0001, 0.001, 42);
            var result = detector.detect(returns);
            assertTrue(result.regime() == RegimeType.LOW_VOL || result.regime() == RegimeType.NORMAL);
            assertTrue(result.confidence() > 0);
        }

        @Test
        void givenHighVolatility_whenDetect_thenHighVol() {
            var returns = new ArrayList<Double>();
            for (int i = 0; i < 60; i++) {
                returns.add(0.0001 + (Math.random() - 0.5) * 0.001);
            }
            for (int i = 0; i < 40; i++) {
                returns.add(0.0001 + (Math.random() - 0.5) * 0.05);
            }
            var result = detector.detect(returns);
            assertTrue(result.regime() == RegimeType.HIGH_VOL || result.regime() == RegimeType.UNSTABLE || result.regime() == RegimeType.METASTABLE);
        }

        @Test
        void givenNullInput_whenDetect_thenNormal() {
            var result = detector.detect(null);
            assertEquals(RegimeType.NORMAL, result.regime());
            assertEquals(0.0, result.confidence());
        }

        @Test
        void givenInsufficientData_whenDetect_thenNormal() {
            var result = detector.detect(List.of(0.01, 0.02, 0.03));
            assertEquals(RegimeType.NORMAL, result.regime());
            assertEquals(0.0, result.confidence());
        }
    }

    @Nested
    class WithExogenousShock {
        @Test
        void givenShock_whenOverride_thenExogenousShock() {
            var base = detector.detect(generateReturns(50, 0.001, 0.005, 42));
            var result = detector.withExogenousShock(base, true);
            assertEquals(RegimeType.EXOGENOUS_SHOCK, result.regime());
            assertEquals(0.95, result.confidence());
        }

        @Test
        void givenNoShock_whenOverride_thenUnchanged() {
            var base = detector.detect(generateReturns(50, 0.001, 0.005, 42));
            var result = detector.withExogenousShock(base, false);
            assertEquals(base.regime(), result.regime());
        }
    }

    @Nested
    class ComputeRollingVol {
        @Test
        void givenValidReturns_whenCompute_thenCorrectLength() {
            var returns = generateReturns(50, 0.001, 0.005, 42);
            double[] vols = detector.computeRollingVol(returns, 20);
            assertEquals(31, vols.length);
        }

        @Test
        void givenInsufficientData_whenCompute_thenEmpty() {
            var returns = generateReturns(10, 0.001, 0.005, 42);
            double[] vols = detector.computeRollingVol(returns, 20);
            assertEquals(0, vols.length);
        }

        @Test
        void givenZeroReturns_whenCompute_thenZeroVol() {
            var returns = new ArrayList<Double>();
            for (int i = 0; i < 30; i++) {
                returns.add(0.001);
            }
            double[] vols = detector.computeRollingVol(returns, 20);
            for (double v : vols) {
                assertEquals(0.0, v, 1e-9);
            }
        }
    }
}
