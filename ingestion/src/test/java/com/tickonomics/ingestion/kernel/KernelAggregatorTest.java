package com.tickonomics.ingestion.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class KernelAggregatorTest {

    private final KernelAggregator aggregator = new KernelAggregator();

    @Test
    @DisplayName("Given constant prices, when computeRealizedVariance, then RV is zero")
    void constantPricesYieldZeroRV() {
        List<Double> prices = List.of(100.0, 100.0, 100.0, 100.0, 100.0);

        double rv = aggregator.computeRealizedVariance(prices, KernelAggregator.TUKEY_HANNING, 2);

        assertEquals(0.0, rv, 1e-10);
    }

    @Test
    @DisplayName("Given random walk prices, when computeRealizedVariance with Tukey-Hanning, then RV is positive")
    void randomWalkYieldsPositiveRV() {
        Random rng = new Random(42);
        List<Double> prices = new ArrayList<>();
        double p = 100.0;
        for (int i = 0; i < 200; i++) {
            p += rng.nextGaussian() * 0.5;
            prices.add(p);
        }

        double rvTukey = aggregator.computeRealizedVariance(prices, KernelAggregator.TUKEY_HANNING, 10);
        double rvParzen = aggregator.computeRealizedVariance(prices, KernelAggregator.PARZEN, 10);

        assertTrue(rvTukey > 0, "Tukey-Hanning RV should be positive");
        assertTrue(rvParzen > 0, "Parzen RV should be positive");
    }

    @Test
    @DisplayName("Given single price, when computeRealizedVariance, then returns zero")
    void singlePriceReturnsZero() {
        List<Double> prices = List.of(100.0);

        double rv = aggregator.computeRealizedVariance(prices, KernelAggregator.TUKEY_HANNING, 5);

        assertEquals(0.0, rv, 1e-10);
    }

    @Test
    @DisplayName("Given Tukey-Hanning kernel at x=0, then returns 1.0")
    void tukeyHanningAtZero() {
        assertEquals(1.0, KernelAggregator.TUKEY_HANNING.apply(0.0), 1e-10);
    }

    @Test
    @DisplayName("Given Tukey-Hanning kernel at x=1, then returns 0.0")
    void tukeyHanningAtOne() {
        assertEquals(0.0, KernelAggregator.TUKEY_HANNING.apply(1.0), 1e-10);
    }

    @Test
    @DisplayName("Given Parzen kernel at x=0, then returns 1.0")
    void parzenAtZero() {
        assertEquals(1.0, KernelAggregator.PARZEN.apply(0.0), 1e-10);
    }

    @Test
    @DisplayName("Given Parzen kernel at x>1, then returns 0.0")
    void parzenBeyondOne() {
        assertEquals(0.0, KernelAggregator.PARZEN.apply(1.5), 1e-10);
    }
}
