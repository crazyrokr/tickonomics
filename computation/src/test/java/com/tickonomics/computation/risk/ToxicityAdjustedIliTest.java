package com.tickonomics.computation.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ToxicityAdjustedIliTest {

    private ToxicityAdjustedIli service;

    @BeforeEach
    void setUp() {
        service = new ToxicityAdjustedIli();
    }

    @Nested
    class HarmfulToxicity {

        @Test
        void givenHarmfulToxicity_whenAdjust_thenReducesIliBySeventyPercent() {
            /*
             * Given: ili=1.0 and toxicityClass="HARMFUL"
             * When: adjust() is called
             * Then: adjustedIli = 1.0 * 0.7 = 0.7
             */
            var result = service.adjust(1.0, "HARMFUL");

            assertEquals(1.0, result.originalIli(), 1e-10);
            assertEquals(0.7, result.adjustedIli(), 1e-10);
            assertEquals("HARMFUL", result.toxicityClass());
            assertEquals(0.7, result.sensitivityModifier(), 1e-10);
        }

        @Test
        void givenLowercaseHarmful_whenAdjust_thenNormalizesCase() {
            /*
             * Given: toxicityClass="harmful" (lowercase)
             * When: adjust() is called
             * Then: toxicityClass is normalized to "HARMFUL"
             */
            var result = service.adjust(1.0, "harmful");

            assertEquals("HARMFUL", result.toxicityClass());
            assertEquals(0.7, result.adjustedIli(), 1e-10);
        }
    }

    @Nested
    class BeneficialToxicity {

        @Test
        void givenBeneficialToxicity_whenAdjust_thenIncreasesIliByTenPercent() {
            /*
             * Given: ili=1.0 and toxicityClass="BENEFICIAL"
             * When: adjust() is called
             * Then: adjustedIli = 1.0 * 1.1 = 1.1
             */
            var result = service.adjust(1.0, "BENEFICIAL");

            assertEquals(1.1, result.adjustedIli(), 1e-10);
            assertEquals("BENEFICIAL", result.toxicityClass());
            assertEquals(1.1, result.sensitivityModifier(), 1e-10);
        }
    }

    @Nested
    class NeutralToxicity {

        @Test
        void givenNeutralToxicity_whenAdjust_thenNoChange() {
            /*
             * Given: ili=1.0 and toxicityClass="NEUTRAL"
             * When: adjust() is called
             * Then: adjustedIli = 1.0 * 1.0 = 1.0
             */
            var result = service.adjust(1.0, "NEUTRAL");

            assertEquals(1.0, result.adjustedIli(), 1e-10);
            assertEquals("NEUTRAL", result.toxicityClass());
            assertEquals(1.0, result.sensitivityModifier(), 1e-10);
        }

        @Test
        void givenNullToxicity_whenAdjust_thenTreatsAsNeutral() {
            /*
             * Given: ili=1.0 and toxicityClass=null
             * When: adjust() is called
             * Then: adjustedIli = 1.0 (no change), class = "NEUTRAL"
             */
            var result = service.adjust(1.0, null);

            assertEquals(1.0, result.adjustedIli(), 1e-10);
            assertEquals("NEUTRAL", result.toxicityClass());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void givenUnknownToxicityString_whenAdjust_thenTreatsAsNeutral() {
            /*
             * Given: an unrecognized toxicity class string
             * When: adjust() is called
             * Then: defaults to neutral behavior
             */
            var result = service.adjust(2.0, "UNKNOWN");

            assertEquals(2.0, result.adjustedIli(), 1e-10);
            assertEquals("UNKNOWN", result.toxicityClass());
        }

        @Test
        void givenZeroIli_whenAdjust_thenZeroResult() {
            /*
             * Given: ili=0.0
             * When: adjust() is called with any toxicity class
             * Then: adjustedIli remains 0.0
             */
            var result = service.adjust(0.0, "HARMFUL");

            assertEquals(0.0, result.adjustedIli(), 1e-10);
        }
    }
}
