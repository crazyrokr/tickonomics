package com.tickonomics.computation.demo;

import com.tickonomics.computation.demo.DemoConfig.GlobalSafeMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cross-module systemic resilience monitor backing Global Safe Mode. Auto-enters Safe Mode when
 * at least {@link GlobalSafeMode#minDegradedIndicators()} of the three health indicators degrade
 * simultaneously (correlated degradation), then <em>latches</em>: recovery requires an explicit
 * manual acknowledgment via {@link #deactivateManual()}. A lone degraded indicator does not trip
 * Safe Mode, which guards against single-source false positives.
 *
 * <p>Health indicators (each counted as degraded when):
 * <ul>
 *   <li><b>Overflow utilization</b> — ingestion buffer fill is at or above the threshold.</li>
 *   <li><b>Analytics worker</b> — reported unhealthy, or its probe latency is at or above the
 *       threshold.</li>
 *   <li><b>Proxy divergence</b> — an intraday proxy dislocation is currently active.</li>
 * </ul>
 *
 * <p>State is in-memory and volatile, mirroring {@link KillSwitch}'s ADR-017 trade-off: demo
 * trading is opt-in ({@code monitor.demo.enabled=false}), so a process restart returning Safe
 * Mode to its inactive default is an acceptable operational trade-off. See ADR-018.
 */
@Service
public class SystemicResilienceMonitor {

  private final GlobalSafeMode config;
  private final ResilienceHealthProbe probe;

  private final AtomicBoolean manualOverride = new AtomicBoolean(false);
  private final AtomicBoolean autoActivated = new AtomicBoolean(false);
  private final AtomicReference<Instant> lastActivationAt = new AtomicReference<>();
  private final AtomicReference<String> lastReason = new AtomicReference<>();
  private volatile ResilienceHealthSnapshot lastSnapshot = ResilienceHealthSnapshot.unknown();

  public SystemicResilienceMonitor(DemoConfig config, ResilienceHealthProbe probe) {
    this.config = config.globalSafeMode();
    this.probe = probe;
  }

  public boolean isSafeModeActive() {
    return config.enabled() && (manualOverride.get() || autoActivated.get());
  }

  /**
   * Evaluates a health reading, latching Safe Mode on when correlated degradation is detected.
   * Never auto-clears; once latched, only {@link #deactivateManual()} releases it.
   */
  public EvaluationResult evaluate(ResilienceHealthSnapshot snapshot) {
    ResilienceHealthSnapshot reading =
        snapshot == null ? ResilienceHealthSnapshot.unknown() : snapshot;
    lastSnapshot = reading;
    List<String> reasons = degradedReasons(reading);
    boolean thresholdMet = reasons.size() >= config.minDegradedIndicators();
    boolean tripped = thresholdMet && config.enabled();
    if (tripped) {
      autoActivated.set(true);
      lastActivationAt.set(Instant.now());
      lastReason.set(String.join(" + ", reasons));
    }
    return new EvaluationResult(reasons, tripped);
  }

  /** Scheduled sweep that samples the live probe; meets the &lt;5s correlation-detection SLA. */
  @Scheduled(fixedDelayString = "${monitor.demo.global-safe-mode.evaluation-interval-ms:5000}")
  public void evaluateCurrent() {
    evaluate(probe.snapshot());
  }

  public void activateManual() {
    manualOverride.set(true);
    lastActivationAt.set(Instant.now());
    lastReason.set("manual_override");
  }

  /** Manual recovery acknowledgment: clears both the manual override and the auto latch. */
  public void deactivateManual() {
    manualOverride.set(false);
    autoActivated.set(false);
  }

  public boolean isRecoveryReady(ResilienceHealthSnapshot snapshot) {
    ResilienceHealthSnapshot reading =
        snapshot == null ? ResilienceHealthSnapshot.unknown() : snapshot;
    return degradedReasons(reading).isEmpty();
  }

  public StatusReport status() {
    ResilienceHealthSnapshot reading = lastSnapshot;
    return new StatusReport(
        isSafeModeActive(),
        autoActivated.get(),
        manualOverride.get(),
        config.enabled(),
        lastReason.get(),
        lastActivationAt.get(),
        degradedReasons(reading),
        isRecoveryReady(reading));
  }

  private List<String> degradedReasons(ResilienceHealthSnapshot reading) {
    List<String> reasons = new ArrayList<>(3);
    if (isOverflowDegraded(reading)) {
      reasons.add("overflow_utilization_exceeded");
    }
    if (isWorkerDegraded(reading)) {
      reasons.add("analytics_worker_degraded");
    }
    if (reading.proxyDivergenceActive()) {
      reasons.add("proxy_divergence_active");
    }
    return reasons;
  }

  private boolean isOverflowDegraded(ResilienceHealthSnapshot reading) {
    double utilization = reading.overflowUtilizationPct();
    return utilization >= 0 && utilization >= config.overflowUtilizationThresholdPct();
  }

  private boolean isWorkerDegraded(ResilienceHealthSnapshot reading) {
    if (!reading.workerHealthy()) {
      return true;
    }
    long latency = reading.workerLatencyMs();
    return latency >= 0 && latency >= config.workerLatencyThresholdMs();
  }

  /** Outcome of a single evaluation: which indicators were degraded and whether Safe Mode tripped. */
  public record EvaluationResult(List<String> degradedIndicators, boolean triggeredNow) {}

  /** Operator-facing view returned by the safe-mode REST endpoints. */
  public record StatusReport(
      boolean active,
      boolean autoActivated,
      boolean manualOverride,
      boolean enabled,
      String lastReason,
      Instant lastActivationAt,
      List<String> degradedIndicators,
      boolean recoveryReady) {}
}
