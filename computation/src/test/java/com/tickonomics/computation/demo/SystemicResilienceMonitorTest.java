package com.tickonomics.computation.demo;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tickonomics.computation.demo.DemoConfig.GlobalSafeMode;
import com.tickonomics.computation.demo.SystemicResilienceMonitor.EvaluationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SystemicResilienceMonitorTest {

  private static final GlobalSafeMode DEFAULTS = GlobalSafeMode.defaults();

  private static SystemicResilienceMonitor monitor(GlobalSafeMode gsm) {
    DemoConfig config = new DemoConfig(
        true, new BigDecimal("100000.00"), 5.0, 5.0, 10.0, true, true, true, 10_000_000.0,
        DemoConfig.AdvancedCostModel.defaults(), DemoConfig.RandomizedExecution.defaults(),
        DemoConfig.MarketStabilityGuard.defaults(), DemoConfig.OrderImpactPredictor.defaults(),
        DemoConfig.MarketMakerMode.defaults(), DemoConfig.DynamicStops.defaults(),
        DemoConfig.PortfolioAlgebra.defaults(), DemoConfig.LeverageRotation.defaults(),
        DemoConfig.KillSwitchConfig.defaults(), gsm);
    return new SystemicResilienceMonitor(config, () -> ResilienceHealthSnapshot.unknown());
  }

  private static ResilienceHealthSnapshot snapshot(double overflowPct, long latencyMs,
      boolean workerHealthy, boolean proxyDivergence) {
    return new ResilienceHealthSnapshot(overflowPct, latencyMs, workerHealthy, proxyDivergence);
  }

  @Nested
  class HealthyAndSingleIndicator {

    @Test
    void givenAllIndicatorsHealthy_whenEvaluate_thenInactiveAndNotTriggered() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(50.0, 100L, true, false));

      assertFalse(result.triggeredNow());
      assertTrue(result.degradedIndicators().isEmpty());
      assertFalse(monitor.isSafeModeActive());
    }

    @Test
    void givenOnlyOverflowDegraded_whenEvaluate_thenNotTriggeredFalsePositiveGuard() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(90.0, 100L, true, false));

      assertFalse(result.triggeredNow());
      assertEquals(1, result.degradedIndicators().size());
      assertFalse(monitor.isSafeModeActive());
    }

    @Test
    void givenOnlyWorkerUnhealthy_whenEvaluate_thenNotTriggeredFalsePositiveGuard() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(50.0, 100L, false, false));

      assertFalse(result.triggeredNow());
      assertEquals(1, result.degradedIndicators().size());
      assertFalse(monitor.isSafeModeActive());
    }

    @Test
    void givenOnlyWorkerLatencyDegraded_whenEvaluate_thenNotTriggeredFalsePositiveGuard() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(50.0, 6000L, true, false));

      assertFalse(result.triggeredNow());
      assertEquals(1, result.degradedIndicators().size());
      assertFalse(monitor.isSafeModeActive());
    }

    @Test
    void givenOnlyProxyDivergence_whenEvaluate_thenNotTriggeredFalsePositiveGuard() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(50.0, 100L, true, true));

      assertFalse(result.triggeredNow());
      assertEquals(1, result.degradedIndicators().size());
      assertFalse(monitor.isSafeModeActive());
    }

    @Test
    void givenUnknownSnapshot_whenEvaluate_thenNoDegradedIndicators() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(ResilienceHealthSnapshot.unknown());

      assertFalse(result.triggeredNow());
      assertTrue(result.degradedIndicators().isEmpty());
      assertFalse(monitor.isSafeModeActive());
    }

    @Test
    void givenNullSnapshot_whenEvaluate_thenTreatedAsUnknownAndInactive() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(null);

      assertFalse(result.triggeredNow());
      assertFalse(monitor.isSafeModeActive());
    }
  }

  @Nested
  class CorrelatedDegradation {

    @Test
    void givenOverflowAndWorkerDegraded_whenEvaluate_thenAutoActivates() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(90.0, 6000L, true, false));

      assertTrue(result.triggeredNow());
      assertEquals(2, result.degradedIndicators().size());
      assertTrue(monitor.isSafeModeActive());
      assertTrue(monitor.status().autoActivated());
      assertFalse(monitor.status().manualOverride());
      assertTrue(monitor.status().lastReason().contains("overflow_utilization_exceeded"));
      assertTrue(monitor.status().lastReason().contains("analytics_worker_degraded"));
    }

    @Test
    void givenWorkerAndProxyDegraded_whenEvaluate_thenAutoActivates() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(50.0, 100L, false, true));

      assertTrue(result.triggeredNow());
      assertTrue(monitor.isSafeModeActive());
    }

    @Test
    void givenAllThreeDegraded_whenEvaluate_thenAutoActivates() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(95.0, 7000L, false, true));

      assertTrue(result.triggeredNow());
      assertEquals(3, result.degradedIndicators().size());
      assertTrue(monitor.isSafeModeActive());
    }

    @Test
    void givenOverflowAtExactThresholdAndWorkerAtExactLatency_whenEvaluate_thenActivates() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(80.0, 5000L, true, false));

      assertTrue(result.triggeredNow());
      assertTrue(monitor.isSafeModeActive());
    }

    @Test
    void givenIndicatorsJustBelowThreshold_whenEvaluate_thenNotTriggered() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      EvaluationResult result = monitor.evaluate(snapshot(79.9, 4999L, true, false));

      assertFalse(result.triggeredNow());
      assertFalse(monitor.isSafeModeActive());
    }
  }

  @Nested
  class LatchingAndRecovery {

    @Test
    void givenAutoActivatedWhenHealthySnapshotReturns_whenEvaluateWithoutAck_thenStaysActive() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);
      monitor.evaluate(snapshot(90.0, 6000L, true, false));
      assertTrue(monitor.isSafeModeActive());

      EvaluationResult recovered = monitor.evaluate(snapshot(10.0, 100L, true, false));

      assertFalse(recovered.triggeredNow());
      assertTrue(monitor.isSafeModeActive(),
          "Safe Mode must latch until a manual recovery acknowledgment clears it");
      assertTrue(monitor.status().recoveryReady(),
          "recoveryReady reflects current health, independent of the latch");
    }

    @Test
    void givenAutoActivatedWhenDeactivateManual_thenInactive() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);
      monitor.evaluate(snapshot(90.0, 6000L, true, false));
      assertTrue(monitor.isSafeModeActive());

      monitor.deactivateManual();

      assertFalse(monitor.isSafeModeActive());
      assertFalse(monitor.status().autoActivated());
    }
  }

  @Nested
  class ManualOverride {

    @Test
    void givenManualActivateOnHealthySnapshot_whenIsSafeModeActive_thenActive() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);

      monitor.activateManual();

      assertTrue(monitor.isSafeModeActive());
      assertTrue(monitor.status().manualOverride());
    }

    @Test
    void givenManualActivatedWhenDeactivateManual_thenInactive() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);
      monitor.activateManual();
      assertTrue(monitor.isSafeModeActive());

      monitor.deactivateManual();

      assertFalse(monitor.isSafeModeActive());
    }

    @Test
    void givenManualAndAutoBothSetWhenDeactivate_thenBothCleared() {
      SystemicResilienceMonitor monitor = monitor(DEFAULTS);
      monitor.evaluate(snapshot(90.0, 6000L, true, false));
      monitor.activateManual();
      assertTrue(monitor.isSafeModeActive());

      monitor.deactivateManual();

      assertFalse(monitor.status().autoActivated());
      assertFalse(monitor.status().manualOverride());
      assertFalse(monitor.isSafeModeActive());
    }
  }

  @Nested
  class DisabledConfig {

    private final GlobalSafeMode disabled = new GlobalSafeMode(false, 80.0, 5000L, 2, 5000L);

    @Test
    void givenDisabledWhenCorrelatedDegradation_whenEvaluate_thenNeverActivates() {
      SystemicResilienceMonitor monitor = monitor(disabled);

      EvaluationResult result = monitor.evaluate(snapshot(90.0, 6000L, false, true));

      assertFalse(result.triggeredNow());
      assertFalse(monitor.isSafeModeActive());
      assertFalse(monitor.status().autoActivated());
    }

    @Test
    void givenDisabledWhenManualActivate_whenIsSafeModeActive_thenStillInactive() {
      SystemicResilienceMonitor monitor = monitor(disabled);

      monitor.activateManual();

      assertFalse(monitor.isSafeModeActive());
    }
  }
}
