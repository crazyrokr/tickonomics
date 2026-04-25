package com.tickonomics.computation.equity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class UniverseAggregatorTest {

    @Test
    void givenKnownSymbolReturns_whenProcessTickCycle_thenUniverseMeanAndStdAreCorrect() {
        UniverseAggregator aggregator = new UniverseAggregator();

        Map<String, Double> returns = new HashMap<>();
        returns.put("AAPL", 0.02);
        returns.put("MSFT", 0.04);
        returns.put("GOOG", 0.06);

        aggregator.processTickCycle(returns);

        UniverseContext context = aggregator.getContext();
        assertNotNull(context);
        assertEquals(3, context.symbolCount());

        double expectedMean = (0.02 + 0.04 + 0.06) / 3.0;
        assertEquals(expectedMean, context.universeMean(), 1e-9);

        double expectedVariance = (Math.pow(0.02 - expectedMean, 2)
            + Math.pow(0.04 - expectedMean, 2)
            + Math.pow(0.06 - expectedMean, 2)) / 3.0;
        double expectedStd = Math.sqrt(expectedVariance);
        assertEquals(expectedStd, context.universeStd(), 1e-9);
    }

    @Test
    void givenSymbolCountExceeds1000_whenProcessTickCycle_thenThrowsIllegalArgumentException() {
        UniverseAggregator aggregator = new UniverseAggregator();

        Map<String, Double> returns = IntStream.range(0, 1001)
            .boxed()
            .collect(Collectors.toMap(i -> "SYM" + i, i -> 0.01));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> aggregator.processTickCycle(returns)
        );
        assertTrue(exception.getMessage().contains("Symbol count exceeds cap"));
    }

    @Test
    void givenAllSameReturns_whenProcessTickCycle_thenUniverseStdIsZero() {
        UniverseAggregator aggregator = new UniverseAggregator();

        Map<String, Double> returns = new HashMap<>();
        returns.put("A", 0.05);
        returns.put("B", 0.05);
        returns.put("C", 0.05);

        aggregator.processTickCycle(returns);

        UniverseContext context = aggregator.getContext();
        assertNotNull(context);
        assertEquals(0.05, context.universeMean(), 1e-9);
        assertEquals(0.0, context.universeStd(), 1e-9);
    }

    @Test
    void givenTwoSequentialCalls_whenGetContext_thenReturnsLatestContext() {
        UniverseAggregator aggregator = new UniverseAggregator();

        Map<String, Double> first = Map.of("A", 0.01, "B", 0.02);
        Map<String, Double> second = Map.of("X", 0.10, "Y", 0.20);

        aggregator.processTickCycle(first);
        aggregator.processTickCycle(second);

        UniverseContext context = aggregator.getContext();
        assertNotNull(context);
        assertEquals(2, context.symbolCount());
        assertEquals(0.15, context.universeMean(), 1e-9);
    }
}
