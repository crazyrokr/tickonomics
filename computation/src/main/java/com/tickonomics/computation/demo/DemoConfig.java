package com.tickonomics.computation.demo;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor.demo")
public record DemoConfig(
    boolean enabled,
    BigDecimal virtualBalance,
    double positionSizePct,
    double stopLossPct,
    double takeProfitPct,
    boolean autoExecuteSignals,
    boolean executeDegradedSignals,
    boolean skipDislocatedSignals,
    double defaultAddv,
    AdvancedCostModel advancedCostModel,
    RandomizedExecution randomizedExecution,
    MarketStabilityGuard marketStabilityGuard,
    OrderImpactPredictor orderImpactPredictor,
    MarketMakerMode marketMakerMode,
    DynamicStops dynamicStops,
    PortfolioAlgebra portfolioAlgebra,
    LeverageRotation leverageRotation,
    KillSwitchConfig killSwitch,
    GlobalSafeMode globalSafeMode) {

  public DemoConfig {
    if (virtualBalance == null || virtualBalance.compareTo(BigDecimal.ZERO) <= 0) {
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

  /**
   * Factory for the original nine core fields, filling the v5 execution-model blocks with
   * conservative defaults. Spring Boot binds the record via its single canonical (full)
   * constructor, so YAML overrides on each nested block still take effect; this factory only
   * keeps direct call sites (tests, {@link #defaults()}) concise without introducing a second
   * constructor, which would conflict with record property binding.
   */
  public static DemoConfig core(boolean enabled, BigDecimal virtualBalance, double positionSizePct,
      double stopLossPct, double takeProfitPct, boolean autoExecuteSignals,
      boolean executeDegradedSignals, boolean skipDislocatedSignals, double defaultAddv) {
    return new DemoConfig(enabled, virtualBalance, positionSizePct, stopLossPct, takeProfitPct,
        autoExecuteSignals, executeDegradedSignals, skipDislocatedSignals, defaultAddv,
        AdvancedCostModel.defaults(), RandomizedExecution.defaults(),
        MarketStabilityGuard.defaults(), OrderImpactPredictor.defaults(),
        MarketMakerMode.defaults(), DynamicStops.defaults(), PortfolioAlgebra.defaults(),
        LeverageRotation.defaults(), KillSwitchConfig.defaults(), GlobalSafeMode.defaults());
  }

  public static DemoConfig defaults() {
    return core(false, new BigDecimal("100000.00"), 5.0, 5.0, 10.0, false, true, true, 10_000_000.0);
  }

  public record AdvancedCostModel(
      boolean enabled,
      double passiveBps,
      double aggressiveBps,
      double largeOrderBps,
      boolean nbboDriftSimulation) {

    public AdvancedCostModel {
      if (passiveBps < 0 || aggressiveBps < 0 || largeOrderBps < 0) {
        throw new IllegalArgumentException("submission-impact bps must not be negative");
      }
    }

    public static AdvancedCostModel defaults() {
      return new AdvancedCostModel(true, 0.84, 2.03, 9.04, true);
    }
  }

  public record RandomizedExecution(boolean enabled, int windowSeconds, boolean avoidRoundMarks) {

    public RandomizedExecution {
      if (windowSeconds <= 0) {
        throw new IllegalArgumentException("windowSeconds must be positive");
      }
    }

    public static RandomizedExecution defaults() {
      return new RandomizedExecution(true, 60, true);
    }
  }

  public record MarketStabilityGuard(
      boolean enabled,
      double volatilityThresholdPct,
      int triggerLatencyMs) {

    public MarketStabilityGuard {
      if (volatilityThresholdPct < 0 || triggerLatencyMs < 0) {
        throw new IllegalArgumentException("volatility threshold and latency must not be negative");
      }
    }

    public static MarketStabilityGuard defaults() {
      return new MarketStabilityGuard(true, 5.0, 100);
    }
  }

  public record OrderImpactPredictor(boolean enabled, double maxImpactThresholdBps) {

    public OrderImpactPredictor {
      if (maxImpactThresholdBps < 0) {
        throw new IllegalArgumentException("maxImpactThresholdBps must not be negative");
      }
    }

    public static OrderImpactPredictor defaults() {
      return new OrderImpactPredictor(true, 10.0);
    }
  }

  public record MarketMakerMode(
      boolean enabled,
      boolean preferLimitOrders,
      double spreadCaptureTargetPct) {

    public MarketMakerMode {
      if (spreadCaptureTargetPct < 0 || spreadCaptureTargetPct > 100) {
        throw new IllegalArgumentException("spreadCaptureTargetPct must be between 0 and 100");
      }
    }

    public static MarketMakerMode defaults() {
      return new MarketMakerMode(false, true, 60.0);
    }
  }

  public record DynamicStops(boolean enabled, String recalibrationInterval) {

    public static DynamicStops defaults() {
      return new DynamicStops(false, "7d");
    }
  }

  public record PortfolioAlgebra(boolean useStandardizedCostModel) {

    public static PortfolioAlgebra defaults() {
      return new PortfolioAlgebra(true);
    }
  }

  public record LeverageRotation(boolean enabled, int maWindowDays, String benchmarkSymbol) {

    public LeverageRotation {
      if (maWindowDays <= 0) {
        throw new IllegalArgumentException("maWindowDays must be positive");
      }
      if (benchmarkSymbol == null || benchmarkSymbol.isBlank()) {
        throw new IllegalArgumentException("benchmarkSymbol must not be blank");
      }
    }

    public static LeverageRotation defaults() {
      return new LeverageRotation(true, 200, "SPY");
    }
  }

  public record KillSwitchConfig(boolean liquidateOnActivate) {

    public static KillSwitchConfig defaults() {
      return new KillSwitchConfig(true);
    }
  }

  /**
   * Cross-module systemic resilience thresholds. Global Safe Mode latches on when at least
   * {@link #minDegradedIndicators()} of the three health indicators (ingestion overflow
   * utilization, analytics worker health/latency, proxy divergence) are degraded simultaneously,
   * and stays latched until a manual recovery acknowledgment clears it. See ADR-018.
   */
  public record GlobalSafeMode(
      boolean enabled,
      double overflowUtilizationThresholdPct,
      long workerLatencyThresholdMs,
      int minDegradedIndicators,
      long evaluationIntervalMs) {

    public GlobalSafeMode {
      if (overflowUtilizationThresholdPct < 0 || overflowUtilizationThresholdPct > 100) {
        throw new IllegalArgumentException(
            "overflowUtilizationThresholdPct must be between 0 and 100");
      }
      if (workerLatencyThresholdMs < 0) {
        throw new IllegalArgumentException("workerLatencyThresholdMs must not be negative");
      }
      if (minDegradedIndicators < 1 || minDegradedIndicators > 3) {
        throw new IllegalArgumentException("minDegradedIndicators must be between 1 and 3");
      }
      if (evaluationIntervalMs <= 0) {
        throw new IllegalArgumentException("evaluationIntervalMs must be positive");
      }
    }

    public static GlobalSafeMode defaults() {
      return new GlobalSafeMode(true, 80.0, 5000L, 2, 5000L);
    }
  }
}
