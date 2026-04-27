package com.tickonomics.computation.talib;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TalibAdapterTest {

    private static TalibAdapter adapter;

    @BeforeAll
    static void setUp() {
        adapter = new TalibAdapter();
    }

    @Nested
    class ComputeSma {
        @Test
        void givenKnownData_whenComputeSma_thenMatchesExpected() {
            double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
            double[] sma = adapter.computeSma(data, 3);
            assertEquals(8, sma.length);
            assertEquals(2.0, sma[0], 1e-9);
            assertEquals(3.0, sma[1], 1e-9);
            assertEquals(9.0, sma[7], 1e-9);
        }

        @Test
        void givenPeriodEqualsLength_whenComputeSma_thenSingleValue() {
            double[] data = {2.0, 4.0, 6.0};
            double[] sma = adapter.computeSma(data, 3);
            assertEquals(1, sma.length);
            assertEquals(4.0, sma[0], 1e-9);
        }
    }

    @Nested
    class ComputeStdDev {
        @Test
        void givenConstantData_whenComputeStdDev_thenZero() {
            double[] data = {5.0, 5.0, 5.0, 5.0, 5.0};
            double[] std = adapter.computeStdDev(data, 3);
            for (double v : std) {
                assertEquals(0.0, v, 1e-9);
            }
        }

        @Test
        void givenKnownData_whenComputeStdDev_thenPositive() {
            double[] data = {1.0, 2.0, 3.0, 4.0, 5.0};
            double[] std = adapter.computeStdDev(data, 3);
            assertTrue(std.length > 0);
            assertTrue(std[0] > 0);
        }
    }

    @Nested
    class ComputeCorrel {
        @Test
        void givenIdenticalSeries_whenComputeCorrel_thenOne() {
            double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
            double[] corr = adapter.computeCorrel(data, data, 5);
            for (double v : corr) {
                assertEquals(1.0, v, 1e-6);
            }
        }

        @Test
        void givenOppositeSeries_whenComputeCorrel_thenMinusOne() {
            double[] x = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
            double[] y = {10.0, 9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0};
            double[] corr = adapter.computeCorrel(x, y, 5);
            for (double v : corr) {
                assertEquals(-1.0, v, 1e-6);
            }
        }

        @Test
        void givenMismatchedLengths_whenComputeCorrel_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.computeCorrel(new double[]{1, 2}, new double[]{1, 2, 3}, 2));
        }
    }

    @Nested
    class ComputeBeta {
        @Test
        void givenIdenticalSeries_whenComputeBeta_thenOne() {
            double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
            double[] beta = adapter.computeBeta(data, data, 5);
            for (double v : beta) {
                assertEquals(1.0, v, 1e-6);
            }
        }

        @Test
        void givenMismatchedLengths_whenComputeBeta_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.computeBeta(new double[]{1}, new double[]{1, 2}, 1));
        }
    }

    @Nested
    class ComputeLinearRegSlope {
        @Test
        void givenLinearData_whenComputeSlope_thenKnownSlope() {
            double[] data = {1.0, 3.0, 5.0, 7.0, 9.0, 11.0, 13.0, 15.0, 17.0, 19.0};
            double[] slopes = adapter.computeLinearRegSlope(data, 5);
            assertTrue(slopes.length > 0);
            for (double s : slopes) {
                assertEquals(2.0, s, 1e-6);
            }
        }

        @Test
        void givenConstantData_whenComputeSlope_thenZero() {
            double[] data = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
            double[] slopes = adapter.computeLinearRegSlope(data, 3);
            for (double s : slopes) {
                assertEquals(0.0, s, 1e-9);
            }
        }
    }

    @Nested
    class ComputeBollingerBands {
        @Test
        void givenKnownData_whenComputeBands_thenMiddleEqualsSma() {
            double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
            BBandsResult bands = adapter.computeBollingerBands(data, 5, 2.0, 2.0);
            double[] sma = adapter.computeSma(data, 5);
            assertArrayEquals(sma, bands.validMiddle(), 1e-9);
        }

        @Test
        void givenKnownData_whenComputeBands_thenUpperAboveLower() {
            double[] data = {10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0};
            BBandsResult bands = adapter.computeBollingerBands(data, 4, 2.0, 2.0);
            double[] upper = bands.validUpper();
            double[] lower = bands.validLower();
            for (int i = 0; i < upper.length; i++) {
                assertTrue(upper[i] >= lower[i], "Upper band should be >= lower band");
            }
        }
    }

    @Nested
    class ComputeRoc {
        @Test
        void givenKnownData_whenComputeRoc_thenMatchesExpected() {
            double[] data = {100.0, 105.0, 110.0, 99.0, 115.0};
            double[] roc = adapter.computeRoc(data, 1);
            assertEquals(4, roc.length);
            assertEquals(5.0, roc[0], 1e-6);
            assertEquals(4.761904761, roc[1], 1e-6);
            assertEquals(-10.0, roc[2], 1e-6);
            assertEquals(16.16161616, roc[3], 1e-6);
        }

        @Test
        void givenPeriodLargerThanData_whenComputeRoc_thenReturnsEmpty() {
            double[] data = {1.0, 2.0};
            double[] roc = adapter.computeRoc(data, 5);
            assertEquals(0, roc.length);
        }
    }

    @Nested
    class ComputeRsi {
        @Test
        void givenMonotonicallyIncreasingData_whenComputeRsi_thenHigh() {
            double[] data = new double[30];
            for (int i = 0; i < data.length; i++) {
                data[i] = i + 1.0;
            }
            double[] rsi = adapter.computeRsi(data, 14);
            assertTrue(rsi.length > 0);
            assertTrue(rsi[rsi.length - 1] > 90.0, "RSI should be high for monotonically increasing data");
        }

        @Test
        void givenMonotonicallyDecreasingData_whenComputeRsi_thenLow() {
            double[] data = new double[30];
            for (int i = 0; i < data.length; i++) {
                data[i] = 30.0 - i;
            }
            double[] rsi = adapter.computeRsi(data, 14);
            assertTrue(rsi.length > 0);
            assertTrue(rsi[rsi.length - 1] < 10.0, "RSI should be low for monotonically decreasing data");
        }
    }

    @Nested
    class InputValidation {
        @Test
        void givenNullData_whenComputeSma_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.computeSma(null, 5));
        }

        @Test
        void givenEmptyData_whenComputeSma_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.computeSma(new double[0], 5));
        }

        @Test
        void givenNullData_whenComputeCorrel_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.computeCorrel(null, new double[]{1}, 5));
        }

        @Test
        void givenNullData_whenComputeBeta_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.computeBeta(null, new double[]{1}, 5));
        }
    }
}
