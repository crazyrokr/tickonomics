package com.tickonomics.computation.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor.demo")
public record DemoConfig(
    boolean enabled,
    double virtualBalance,
    double positionSizePct,
    double stopLossPct,
    double takeProfitPct,
    boolean autoExecuteSignals,
    boolean executeDegradedSignals,
    boolean skipDislocatedSignals,
    double defaultAddv) {

  public DemoConfig {
    if (virtualBalance <= 0) {
      throw new IllegalArgumentException("virtualBalance must be positive");
    }
    if (positionSizePct <= 0 || positionSizePct > 100) {
      throw new IllegalArgumentException("positionSizePct must be between 0 and 100");
    }
    if (stopLossPct <= 0 || stopLossPct >= 100) {
      throw new IllegalArgumentException("stopLossPct must be between 0 and 100 exclusive");
    }
    if (takeProfitPct <= 0) {
      throw new IllegalArgumentException("takeProfitPct must be positive");
    }
    if (defaultAddv <= 0) {
      throw new IllegalArgumentException("defaultAddv must be positive");
    }
  }

  public static DemoConfig defaults() {
    return new DemoConfig(
        false, 100_000.0, 5.0, 5.0, 10.0, false, true, true, 10_000_000.0);
  }
}
