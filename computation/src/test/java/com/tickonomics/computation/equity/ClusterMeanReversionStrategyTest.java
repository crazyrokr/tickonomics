package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClusterMeanReversionStrategyTest {

    private UniverseAggregator aggregator;
    private ClusterMeanReversionStrategy strategy;

    @BeforeEach
    void setUp() {
        aggregator = new UniverseAggregator();
        strategy = new ClusterMeanReversionStrategy(aggregator);
    }

    @Test
    void givenExtremeOutlierAboveMean_whenCompute_thenSignalsShortReversion() {
        Map<String, Double> aggregatorReturns = new HashMap<>();
        aggregatorReturns.put("A", 0.01);
        aggregatorReturns.put("B", 0.02);
        aggregatorReturns.put("C", 0.03);
        aggregator.processTickCycle(aggregatorReturns);

        Map<String, Double> signalReturns = new HashMap<>();
        signalReturns.put("A", 0.01);
        signalReturns.put("B", 0.02);
        signalReturns.put("C", 0.50);

        StrategyContext ctx = new StrategyContext(0.95, 0.8);
        AlphaSignal signal = strategy.compute(signalReturns, ctx);

        assertNotNull(signal);
        assertEquals("SHORT", signal.direction());
        assertTrue(signal.strength() > 0.0);
    }

    @Test
    void givenUniformReturns_whenCompute_thenReturnsNeutral() {
        Map<String, Double> returns = new HashMap<>();
        returns.put("A", 0.02);
        returns.put("B", 0.02);
        returns.put("C", 0.02);
        aggregator.processTickCycle(returns);

        StrategyContext ctx = new StrategyContext(0.95, 0.8);
        AlphaSignal signal = strategy.compute(returns, ctx);

        assertEquals("NEUTRAL", signal.direction());
    }

    @Test
    void givenExtremeOutlierBelowMean_whenCompute_thenSignalsLongReversion() {
        Map<String, Double> aggregatorReturns = new HashMap<>();
        aggregatorReturns.put("A", 0.01);
        aggregatorReturns.put("B", 0.02);
        aggregatorReturns.put("C", 0.03);
        aggregator.processTickCycle(aggregatorReturns);

        Map<String, Double> signalReturns = new HashMap<>();
        signalReturns.put("A", -0.40);
        signalReturns.put("B", 0.02);
        signalReturns.put("C", 0.03);

        StrategyContext ctx = new StrategyContext(0.95, 0.8);
        AlphaSignal signal = strategy.compute(signalReturns, ctx);

        assertNotNull(signal);
        assertEquals("LONG", signal.direction());
        assertTrue(signal.strength() > 0.0);
    }

    @Test
    void givenNoUniverseContext_whenCompute_thenReturnsNeutral() {
        StrategyContext ctx = new StrategyContext(0.95, 0.8);
        AlphaSignal signal = strategy.compute(Map.of("A", 0.01), ctx);

        assertEquals("NEUTRAL", signal.direction());
    }
}
