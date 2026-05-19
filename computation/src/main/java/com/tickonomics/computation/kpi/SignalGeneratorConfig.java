package com.tickonomics.computation.kpi;

public record SignalGeneratorConfig(
    double buyThreshold,
    double sellThreshold,
    double transactionCostBps,
    long cooldownMs
) {}
