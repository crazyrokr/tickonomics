package com.tickonomics.computation.optimization;

public record OptimizationProfile(
    String name,
    double learningRate,
    double maxDriftPct,
    String objective) {

    public static final OptimizationProfile PROFIT_MAXIMIZER = new OptimizationProfile(
            "profit_maximizer", 0.01, 10.0, "maximize_sharpe");

    public static final OptimizationProfile THROUGHPUT_MAXIMIZER = new OptimizationProfile(
            "throughput_maximizer", 0.005, 5.0, "minimize_latency");

    public static final OptimizationProfile REGIME_ADAPTOR = new OptimizationProfile(
            "regime_adaptor", 0.02, 10.0, "maximize_hit_rate");
}
