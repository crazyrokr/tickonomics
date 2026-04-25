package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MomentumDecileStrategyTest {

    private UniverseAggregator aggregator;
    private MomentumDecileStrategy strategy;

    @BeforeEach
    void setUp() {
        aggregator = new UniverseAggregator();
        strategy = new MomentumDecileStrategy(aggregator);
    }

    @Test
    void givenPositiveMomentumSpread_whenCompute_thenDirectionIsLong() {
        Map<String, Double> returns = new HashMap<>();
        IntStream.range(0, 100).forEach(i -> returns.put("SYM" + i, i * 0.001));
        aggregator.processTickCycle(returns);

        StrategyContext ctx = new StrategyContext(0.95, 0.8);
        AlphaSignal signal = strategy.compute(returns, ctx);

        assertNotNull(signal);
        assertEquals("LONG", signal.direction());
        assertTrue(signal.strength() > 0.0);
    }

    @Test
    void givenLowIrScore_whenCompute_thenReturnsNeutral() {
        Map<String, Double> returns = Map.of("A", 0.01);
        aggregator.processTickCycle(returns);

        StrategyContext ctx = new StrategyContext(0.8, 0.5);
        AlphaSignal signal = strategy.compute(returns, ctx);

        assertEquals("NEUTRAL", signal.direction());
        assertEquals(0.0, signal.strength(), 1e-9);
    }

    @Test
    void givenEmptyReturns_whenCompute_thenReturnsNeutral() {
        aggregator.processTickCycle(Map.of("A", 0.01));

        StrategyContext ctx = new StrategyContext(0.95, 0.8);
        AlphaSignal signal = strategy.compute(Map.of(), ctx);

        assertEquals("NEUTRAL", signal.direction());
    }

    @Test
    void givenNoUniverseContext_whenCompute_thenReturnsNeutral() {
        StrategyContext ctx = new StrategyContext(0.95, 0.8);
        AlphaSignal signal = strategy.compute(Map.of("A", 0.01), ctx);

        assertEquals("NEUTRAL", signal.direction());
    }
}
