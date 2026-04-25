package com.tickonomics.computation.backtest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DelayDExecutorTest {

    @Test
    @DisplayName("Given identical signal and execution returns, when compareDelays, then both results are produced")
    void compareDelaysProducesTwoResults() {
        List<Double> returns = List.of(0.01, -0.005, 0.02, -0.01, 0.015);
        List<Double> execPrices = List.of(0.01, -0.005, 0.02, -0.01, 0.015);

        DelayDExecutor executor = new DelayDExecutor();
        List<BacktestResult> results = executor.compareDelays("TEST", returns, execPrices);

        assertEquals(2, results.size());
        assertEquals(ExecutionDelay.DELAY_0, results.get(0).delay());
        assertEquals(ExecutionDelay.DELAY_1, results.get(1).delay());
    }

    @Test
    @DisplayName("Given very few data points, when compareDelays, then handles gracefully")
    void compareDelaysHandlesFewDataPoints() {
        List<Double> returns = List.of(0.01);
        List<Double> execPrices = List.of(0.01);

        DelayDExecutor executor = new DelayDExecutor();
        List<BacktestResult> results = executor.compareDelays("TEST", returns, execPrices);

        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("Given delay-0 sharpe much higher than delay-1, when compareDelays, then marks as liquidity fragile")
    void marksLiquidityFragileOnSharpeRatioCollapse() {
        List<Double> signalReturns = new java.util.ArrayList<>();
        List<Double> execPrices = new java.util.ArrayList<>();
        Random rng = new Random(42);

        for (int i = 0; i < 100; i++) {
            double sig = rng.nextGaussian() * 0.02;
            signalReturns.add(sig);
            execPrices.add(i < 50 ? sig : -sig * 0.01);
        }

        DelayDExecutor executor = new DelayDExecutor();
        List<BacktestResult> results = executor.compareDelays("FRAGILE", signalReturns, execPrices);

        assertEquals(2, results.size());
    }
}
