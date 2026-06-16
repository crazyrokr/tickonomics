package com.tickonomics.computation.demo;

import java.math.BigDecimal;

/**
 * Simulated fill produced by an execution handler ({@link PassiveExecutionHandler} or
 * {@link SniperExecutionHandler}); converted into {@code ComparativeExecutionAnalysis.ExecutionSlippage}
 * samples so passive vs. aggressive strategies can be compared in the demo report.
 */
public record FillEstimate(String type, BigDecimal fillPrice, double slippageBps, boolean filled) {}
