package com.tickonomics.computation.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DefiningRangeServiceTest {

    private DefiningRangeService service;

    @BeforeEach
    void setUp() {
        service = new DefiningRangeService();
    }

    @Nested
    class ConfidenceDetermination {

        @Test
        void givenPriceNearSessionLow_whenEvaluate_thenHighConfidence() {
            /*
             * Given: currentPrice=100.5, sessionHigh=110, sessionLow=100
             * When: evaluate() is called
             * Then: proximityToLow is very small, confidence = "HIGH"
             */
            var result = service.evaluate(110.0, 100.0, 100.5, 0);

            assertEquals(DefiningRangeService.CONFIDENCE_HIGH, result.confidence());
            assertTrue(result.proximityToLow() <= 0.1);
        }

        @Test
        void givenPriceNearSessionHigh_whenEvaluate_thenHighConfidence() {
            /*
             * Given: currentPrice=109.5, sessionHigh=110, sessionLow=100
             * When: evaluate() is called
             * Then: proximityToHigh is very small, confidence = "HIGH"
             */
            var result = service.evaluate(110.0, 100.0, 109.5, 0);

            assertEquals(DefiningRangeService.CONFIDENCE_HIGH, result.confidence());
            assertTrue(result.proximityToHigh() <= 0.1);
        }

        @Test
        void givenPriceInsideRange_whenEvaluate_thenMediumConfidence() {
            /*
             * Given: currentPrice=105 (mid-range), sessionHigh=110, sessionLow=100
             * When: evaluate() is called
             * Then: confidence = "MEDIUM"
             */
            var result = service.evaluate(110.0, 100.0, 105.0, 0);

            assertEquals(DefiningRangeService.CONFIDENCE_MEDIUM, result.confidence());
        }

        @Test
        void givenPriceOutsideRange_whenEvaluate_thenLowConfidence() {
            /*
             * Given: currentPrice=115 (above sessionHigh=110)
             * When: evaluate() is called
             * Then: confidence = "LOW"
             */
            var result = service.evaluate(110.0, 100.0, 115.0, 0);

            assertEquals(DefiningRangeService.CONFIDENCE_LOW, result.confidence());
        }

        @Test
        void givenPriceBelowRange_whenEvaluate_thenLowConfidence() {
            /*
             * Given: currentPrice=95 (below sessionLow=100)
             * When: evaluate() is called
             * Then: confidence = "LOW"
             */
            var result = service.evaluate(110.0, 100.0, 95.0, 0);

            assertEquals(DefiningRangeService.CONFIDENCE_LOW, result.confidence());
        }
    }

    @Nested
    class ProximityCalculations {

        @Test
        void givenPriceAtSessionHigh_whenEvaluate_thenProximityToHighZero() {
            /*
             * Given: currentPrice equals sessionHigh
             * When: evaluate() is called
             * Then: proximityToHigh = 0.0
             */
            var result = service.evaluate(110.0, 100.0, 110.0, 0);

            assertEquals(0.0, result.proximityToHigh(), 1e-10);
        }

        @Test
        void givenPriceAtSessionLow_whenEvaluate_thenProximityToLowZero() {
            /*
             * Given: currentPrice equals sessionLow
             * When: evaluate() is called
             * Then: proximityToLow = 0.0
             */
            var result = service.evaluate(110.0, 100.0, 100.0, 0);

            assertEquals(0.0, result.proximityToLow(), 1e-10);
        }

        @Test
        void givenValidRange_whenEvaluate_thenDefiningRangeIsDifference() {
            /*
             * Given: sessionHigh=110 and sessionLow=100
             * When: evaluate() is called
             * Then: definingRange = 10.0
             */
            var result = service.evaluate(110.0, 100.0, 105.0, 0);

            assertEquals(10.0, result.definingRange(), 1e-10);
        }
    }

    @Nested
    class NewsDecay {

        @Test
        void givenZeroMinutes_whenApplyNewsDecay_thenNoDecay() {
            /*
             * Given: confidence=1.0 and minutes=0
             * When: applyNewsDecay() is called
             * Then: result = 1.0 (no decay)
             */
            double decayed = service.applyNewsDecay(1.0, 0.0);

            assertEquals(1.0, decayed, 1e-10);
        }

        @Test
        void givenThirtyMinutes_whenApplyNewsDecay_thenExponentialDecay() {
            /*
             * Given: confidence=1.0 and minutes=30 (one time constant)
             * When: applyNewsDecay() is called
             * Then: result = 1.0 * exp(-1) ≈ 0.3679
             */
            double decayed = service.applyNewsDecay(1.0, 30.0);

            assertEquals(Math.exp(-1.0), decayed, 1e-10);
        }

        @Test
        void givenSixtyMinutes_whenApplyNewsDecay_thenSignificantDecay() {
            /*
             * Given: confidence=1.0 and minutes=60 (two time constants)
             * When: applyNewsDecay() is called
             * Then: result = 1.0 * exp(-2) ≈ 0.1353
             */
            double decayed = service.applyNewsDecay(1.0, 60.0);

            assertEquals(Math.exp(-2.0), decayed, 1e-10);
        }

        @Test
        void givenFullEvaluation_whenNewsDecayApplied_thenDecayedConfidencePresent() {
            /*
             * Given: sessionHigh=110, sessionLow=100, currentPrice=105, 30 min since news
             * When: evaluate() is called
             * Then: newsDecayedConfidence is exponentially decayed
             */
            var result = service.evaluate(110.0, 100.0, 105.0, 30);

            assertTrue(result.newsDecayedConfidence() < 1.0);
            assertTrue(result.newsDecayedConfidence() > 0.0);
        }
    }
}
