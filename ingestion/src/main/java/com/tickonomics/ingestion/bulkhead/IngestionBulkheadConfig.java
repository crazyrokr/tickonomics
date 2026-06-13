package com.tickonomics.ingestion.bulkhead;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bulkhead configuration anchor for the ingestion layer. Holds the canonical names of the three
 * Resilience4j semaphore bulkheads used across ingestion and computation, and hosts the
 * {@link BulkheadPressureMonitor} that emits the {@code BULKHEAD_POOL_PRESSURE} alert whenever a
 * pool crosses 80% utilization.
 *
 * <p>The effective pool sizes are declared in {@code resilience4j.bulkhead.instances.*}; the
 * monitor reads live capacity from the {@link BulkheadRegistry} so it stays correct regardless of
 * how the configs were sourced.</p>
 */
@Configuration
public class IngestionBulkheadConfig {

  public static final String CRITICAL_INGESTION = "criticalIngestion";
  public static final String HIGH_VOLUME_INGESTION = "highVolumeIngestion";
  public static final String COMPUTATION_ENGINE = "computationEngine";

  static final double PRESSURE_THRESHOLD = 0.80;

  /**
   * Periodically samples the three ingestion bulkheads and logs {@code BULKHEAD_POOL_PRESSURE} once
   * per transition into the pressured state, so a sustained 100% Finnhub-WS pool never starves the
   * critical FRED/NY-Fed pool silently.
   */
  @Component
  public static class BulkheadPressureMonitor {

    private static final Logger log = LoggerFactory.getLogger(BulkheadPressureMonitor.class);

    private final BulkheadRegistry registry;
    private final Map<String, AtomicBoolean> underPressure = Map.of(
        CRITICAL_INGESTION, new AtomicBoolean(false),
        HIGH_VOLUME_INGESTION, new AtomicBoolean(false),
        COMPUTATION_ENGINE, new AtomicBoolean(false));

    public BulkheadPressureMonitor(BulkheadRegistry registry) {
      this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${monitor.ingestion.bulkhead.pressure-check-ms:30000}")
    public void checkPressure() {
      underPressure.keySet().forEach(name -> evaluate(name, underPressure.get(name)));
    }

    private void evaluate(String name, AtomicBoolean flag) {
      Bulkhead bulkhead = registry.find(name).orElse(null);
      if (bulkhead == null) {
        return;
      }
      int max = bulkhead.getBulkheadConfig().getMaxConcurrentCalls();
      if (max <= 0) {
        return;
      }
      int available = bulkhead.getMetrics().getAvailableConcurrentCalls();
      int inUse = max - available;
      double utilization = inUse / (double) max;

      if (utilization >= PRESSURE_THRESHOLD) {
        if (flag.compareAndSet(false, true)) {
          log.warn("BULKHEAD_POOL_PRESSURE: pool '{}' at {}% utilization ({}/{} calls in use)",
              name, Math.round(utilization * 100), inUse, max);
        }
      } else {
        flag.set(false);
      }
    }
  }
}
