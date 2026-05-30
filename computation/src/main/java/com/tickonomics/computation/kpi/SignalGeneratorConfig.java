package com.tickonomics.computation.kpi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signal.generator")
public record SignalGeneratorConfig(
    double buyThreshold,
    double sellThreshold,
    double transactionCostBps,
    long cooldownMs
) {
  public SignalGeneratorConfig {
    if (buyThreshold == 0.0) buyThreshold = 0.85;
    if (sellThreshold == 0.0) sellThreshold = 0.15;
    if (transactionCostBps == 0.0) transactionCostBps = 1.5;
    if (cooldownMs == 0) cooldownMs = 300_000L;
  }
}
