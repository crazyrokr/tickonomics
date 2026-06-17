package com.tickonomics;

import com.tickonomics.computation.demo.ResilienceHealthProbe;
import com.tickonomics.computation.demo.ResilienceHealthSnapshot;
import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import com.tickonomics.ingestion.buffer.IngestionBuffer;
import com.tickonomics.ingestion.quality.ProxyDivergenceGuard;
import com.tickonomics.ingestion.ws.FinnhubWsClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cross-module health probe feeding Global Safe Mode's health indicators: ingestion-buffer
 * overflow utilization, analytics-worker health/latency, proxy divergence, and Finnhub WebSocket
 * error state. Lives in the app module because only it sees both the ingestion and computation
 * beans (see ADR-018).
 *
 * <p>Defensive by design: every input is optional. A missing buffer bean, a thrown divergence
 * check, or an unreachable analytics worker yields an unknown/non-degrading reading rather than an
 * exception, so a partially wired deployment never crashes the monitor and cannot trip Safe Mode
 * through missing data alone.
 */
@Component
public class CrossModuleResilienceHealthProbe implements ResilienceHealthProbe {

  private final AnalyticsWorkerClient analyticsWorkerClient;
  private final ProxyDivergenceGuard proxyDivergenceGuard;
  private final ObjectProvider<IngestionBuffer<?>> bufferProvider;
  private final ObjectProvider<FinnhubWsClient> wsClientProvider;
  private final int bufferCapacity;

  public CrossModuleResilienceHealthProbe(
      AnalyticsWorkerClient analyticsWorkerClient,
      ProxyDivergenceGuard proxyDivergenceGuard,
      ObjectProvider<IngestionBuffer<?>> bufferProvider,
      ObjectProvider<FinnhubWsClient> wsClientProvider,
      @Value("${monitor.ingestion.resilience.buffer-capacity:10000}") int bufferCapacity) {
    this.analyticsWorkerClient = analyticsWorkerClient;
    this.proxyDivergenceGuard = proxyDivergenceGuard;
    this.bufferProvider = bufferProvider;
    this.wsClientProvider = wsClientProvider;
    this.bufferCapacity = bufferCapacity;
  }

  @Override
  public ResilienceHealthSnapshot snapshot() {
    long startNanos = System.nanoTime();
    boolean workerHealthy = isWorkerHealthy();
    long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
    long workerLatency =
        workerHealthy ? latencyMs : ResilienceHealthSnapshot.UNKNOWN_LATENCY;

    return new ResilienceHealthSnapshot(
        overflowUtilization(),
        workerLatency,
        workerHealthy,
        proxyDivergenceActive(),
        isWsHealthy());
  }

  private boolean isWsHealthy() {
    FinnhubWsClient client = wsClientProvider.getIfAvailable();
    if (client == null) {
      return true;
    }
    try {
      return !client.hasRecentIoErrors();
    } catch (RuntimeException e) {
      return true;
    }
  }

  private double overflowUtilization() {
    IngestionBuffer<?> buffer = bufferProvider.getIfAvailable();
    if (buffer == null || bufferCapacity <= 0) {
      return ResilienceHealthSnapshot.UNKNOWN_UTILIZATION;
    }
    try {
      return 100.0 * buffer.size() / bufferCapacity;
    } catch (RuntimeException e) {
      return ResilienceHealthSnapshot.UNKNOWN_UTILIZATION;
    }
  }

  private boolean isWorkerHealthy() {
    try {
      return analyticsWorkerClient.isHealthy();
    } catch (RuntimeException e) {
      return false;
    }
  }

  private boolean proxyDivergenceActive() {
    try {
      return proxyDivergenceGuard.checkDivergence().divergent();
    } catch (RuntimeException e) {
      return false;
    }
  }
}
