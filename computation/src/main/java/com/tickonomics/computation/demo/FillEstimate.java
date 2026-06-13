package com.tickonomics.computation.demo;

/**
 * Simulated fill produced by an execution handler ({@link PassiveExecutionHandler} or
 * {@link SniperExecutionHandler}); converted into {@code ComparativeExecutionAnalysis.ExecutionSlippage}
 * samples so passive vs. aggressive strategies can be compared in the demo report.
 */
public record FillEstimate(String type, double fillPrice, double slippageBps, boolean filled) {}
