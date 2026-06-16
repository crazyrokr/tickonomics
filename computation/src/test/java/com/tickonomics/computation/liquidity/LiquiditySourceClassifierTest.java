package com.tickonomics.computation.liquidity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class LiquiditySourceClassifierTest {

    private LiquiditySourceClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new LiquiditySourceClassifier();
    }

    @Nested
    class Normalization {

        @Test
        void givenValidInputs_whenClassify_thenEstimatesSumToOne() {
            /*
             * Given: typical market microstructure signals
             * When: classify() is called
             * Then: all estimates sum to 1.0
             */
            var result = classifier.classify(0.5, 0.5, 0.3, 0.4);

            double sum = result.algo() + result.institutional() + result.professional() + result.retail();
            assertEquals(1.0, sum, 1e-10);
        }

        @Test
        void givenAllZeroInputs_whenClassify_thenEstimatesStillSumToOne() {
            /*
             * Given: all zero inputs
             * When: classify() is called
             * Then: all estimates equal 0.25 and sum to 1.0
             */
            var result = classifier.classify(0.0, 0.0, 0.0, 0.0);

            double sum = result.algo() + result.institutional() + result.professional() + result.retail();
            assertEquals(1.0, sum, 1e-10);
        }

        @Test
        void givenHighFrequencyHighCancellation_whenClassify_thenAlgoDominant() {
            /*
             * Given: high order frequency and high cancellation rate (algo signatures)
             * When: classify() is called
             * Then: algo is the dominant type
             */
            var result = classifier.classify(0.2, 0.9, 0.8, 0.1);

            assertEquals("ALGO", result.dominantType());
            assertTrue(result.algo() > result.institutional());
            assertTrue(result.algo() > result.retail());
        }

        @Test
        void givenLargeOrdersLowFrequency_whenClassify_thenInstitutionalDominant() {
            /*
             * Given: large order size and low frequency (institutional signatures)
             * When: classify() is called
             * Then: institutional is the dominant type
             */
            var result = classifier.classify(0.9, 0.1, 0.2, 0.1);

            assertEquals("INSTITUTIONAL", result.dominantType());
            assertTrue(result.institutional() > result.algo());
        }

        @Test
        void givenLowCancellationHighSpreadCrossing_whenClassify_thenProfessionalDominant() {
            /*
             * Given: low cancellation and high spread crossing (professional signatures)
             * When: classify() is called
             * Then: professional is the dominant type
             */
            var result = classifier.classify(0.5, 0.3, 0.1, 0.9);

            assertEquals("PROFESSIONAL", result.dominantType());
            assertTrue(result.professional() > result.algo());
        }

        @Test
        void givenHighSpreadCrossingSmallOrders_whenClassify_thenRetailDominant() {
            /*
             * Given: high spread crossing and small order size (retail signatures)
             * When: classify() is called
             * Then: retail is the dominant type
             */
            var result = classifier.classify(0.1, 0.2, 0.3, 0.8);

            assertEquals("RETAIL", result.dominantType());
            assertTrue(result.retail() > result.algo());
            assertTrue(result.retail() > result.institutional());
        }
    }

    @Nested
    class BoundaryInputs {

        @Test
        void givenMaxInputs_whenClassify_thenAllEstimatesAreValid() {
            /*
             * Given: all inputs at maximum (1.0)
             * When: classify() is called
             * Then: all estimates are positive and sum to 1.0
             */
            var result = classifier.classify(1.0, 1.0, 1.0, 1.0);

            assertTrue(result.algo() > 0);
            assertTrue(result.institutional() > 0);
            assertTrue(result.professional() > 0);
            assertTrue(result.retail() > 0);
            double sum = result.algo() + result.institutional() + result.professional() + result.retail();
            assertEquals(1.0, sum, 1e-10);
        }
    }
}
