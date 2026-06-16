package com.tickonomics.computation.sensitivity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MarketSensitivityLibraryTest {

    private MarketSensitivityLibrary library;

    @BeforeEach
    void setUp() {
        library = new MarketSensitivityLibrary();
    }

    @Nested
    class FromKpis {

        @Test
        void givenValidKpis_whenFromKpis_thenReturnCorrectGreeks() {
            // Given standard KPI inputs
            double rrpZ = 2.0;
            double spreadZ = 1.5;
            double volZ = 0.8;
            double corr = 0.6;

            // When computing Greeks
            MarketSensitivityLibrary.SensitivityGreeks greeks = library.fromKpis(rrpZ, spreadZ, volZ, corr);

            // Then each Greek matches the formula
            assertEquals(2.0, greeks.repoDelta(), 0.001);
            assertEquals(2.0 * 0.6, greeks.rateDelta(), 0.001);
            assertEquals(2.0 * 2.0 * 0.5, greeks.rateGamma(), 0.001);
            assertEquals(1.5, greeks.spreadDelta(), 0.001);
            assertEquals(0.8 * 0.8 * 0.6, greeks.volga(), 0.001);
            assertEquals(2.0 * 0.0001, greeks.dv01(), 0.000001);
            assertEquals(2.0 * 2.0 * 0.5 * 0.5, greeks.convexity(), 0.001);
        }

        @Test
        void givenZeroInputs_whenFromKpis_thenReturnAllZeros() {
            // Given all zero inputs
            // When computing Greeks
            MarketSensitivityLibrary.SensitivityGreeks greeks = library.fromKpis(0.0, 0.0, 0.0, 0.0);

            // Then all Greeks are zero
            assertEquals(0.0, greeks.repoDelta(), 0.001);
            assertEquals(0.0, greeks.rateDelta(), 0.001);
            assertEquals(0.0, greeks.rateGamma(), 0.001);
            assertEquals(0.0, greeks.spreadDelta(), 0.001);
            assertEquals(0.0, greeks.volga(), 0.001);
            assertEquals(0.0, greeks.dv01(), 0.001);
            assertEquals(0.0, greeks.convexity(), 0.001);
        }

        @Test
        void givenNegativeZscores_whenFromKpis_thenReflectNegatives() {
            // Given negative z-scores
            double rrpZ = -1.5;
            double spreadZ = -2.0;
            double volZ = -1.0;
            double corr = 0.5;

            // When computing Greeks
            MarketSensitivityLibrary.SensitivityGreeks greeks = library.fromKpis(rrpZ, spreadZ, volZ, corr);

            // Then repoDelta and rateDelta are negative
            assertEquals(-1.5, greeks.repoDelta(), 0.001);
            assertEquals(-0.75, greeks.rateDelta(), 0.001);
            assertEquals(-2.0, greeks.spreadDelta(), 0.001);
            // rateGamma = rrpZ^2 * 0.5 = 2.25 * 0.5 = 1.125 (positive since squared)
            assertEquals(1.125, greeks.rateGamma(), 0.001);
            // volga = volZ^2 * corr = 1.0 * 0.5 = 0.5 (positive since squared)
            assertEquals(0.5, greeks.volga(), 0.001);
        }

        @Test
        void givenHighCorrelation_whenFromKpis_thenRateDeltaAndVolgaIncrease() {
            // Given perfect correlation
            double rrpZ = 1.0;
            double volZ = 1.0;

            // When computing with corr=1.0
            MarketSensitivityLibrary.SensitivityGreeks highCorr =
                    library.fromKpis(rrpZ, 0.0, volZ, 1.0);

            // And with corr=0.0
            MarketSensitivityLibrary.SensitivityGreeks noCorr =
                    library.fromKpis(rrpZ, 0.0, volZ, 0.0);

            // Then high correlation gives larger rateDelta and volga
            assertTrue(highCorr.rateDelta() > noCorr.rateDelta());
            assertTrue(highCorr.volga() > noCorr.volga());
        }

        @Test
        void givenDv01_whenFromKpis_thenDv01IsSmall() {
            // Given rrpZ=3.0
            // When computing Greeks
            MarketSensitivityLibrary.SensitivityGreeks greeks = library.fromKpis(3.0, 0.0, 0.0, 0.0);

            // Then dv01 = 3.0 * 0.0001 = 0.0003
            assertEquals(0.0003, greeks.dv01(), 0.0000001);
        }

        @Test
        void givenConvexity_whenFromKpis_thenConvexityIsHalfRateGamma() {
            // Given rrpZ=2.0
            // When computing Greeks
            MarketSensitivityLibrary.SensitivityGreeks greeks = library.fromKpis(2.0, 0.0, 0.0, 0.0);

            // Then convexity = rateGamma * 0.5 = (2.0^2 * 0.5) * 0.5 = 1.0
            assertEquals(greeks.rateGamma() * 0.5, greeks.convexity(), 0.001);
        }
    }

    @Nested
    class Describe {

        @Test
        void givenValidGreeks_whenDescribe_thenReturnFormattedString() {
            // Given valid Greeks
            MarketSensitivityLibrary.SensitivityGreeks greeks =
                    library.fromKpis(1.5, 0.8, 1.2, 0.5);

            // When describing
            String description = library.describe(greeks);

            // Then formatted string contains all Greek names
            assertTrue(description.contains("Repo Delta"));
            assertTrue(description.contains("Rate Delta"));
            assertTrue(description.contains("Rate Gamma"));
            assertTrue(description.contains("Spread Delta"));
            assertTrue(description.contains("Volga"));
            assertTrue(description.contains("DV01"));
            assertTrue(description.contains("Convexity"));
            assertTrue(description.contains("Market Sensitivity Greeks"));
        }

        @Test
        void givenNullGreeks_whenDescribe_thenReturnNoGreeksMessage() {
            // Given null Greeks
            // When describing
            String description = library.describe(null);

            // Then returns no Greeks message
            assertEquals("No Greeks available", description);
        }

        @Test
        void givenZeroGreeks_whenDescribe_thenFormatsAllZeros() {
            // Given zero Greeks
            MarketSensitivityLibrary.SensitivityGreeks greeks =
                    library.fromKpis(0.0, 0.0, 0.0, 0.0);

            // When describing
            String description = library.describe(greeks);

            // Then contains formatted zeros
            assertNotNull(description);
            assertFalse(description.isEmpty());
        }
    }
}
