package com.tickonomics.ingestion.quality;

import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import com.tickonomics.persistence.entity.RateSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataQualityChecker {

  private static final Logger log = LoggerFactory.getLogger(DataQualityChecker.class);
  private static final double OUTLIER_BPS_THRESHOLD = 50.0;
  private static final double BPS_MULTIPLIER = 10000.0;
  private static final String ANOMALY_ENDPOINT = "/api/v1/anomaly/detect";

  private final AnalyticsWorkerClient analyticsClient;
  private final boolean anomalyEnabled;
  private final Duration asyncTimeout;
  private final boolean fallbackToThreshold;

  private final AtomicBoolean workerReachable = new AtomicBoolean(true);

  public DataQualityChecker(
      AnalyticsWorkerClient analyticsClient,
      @Value("${monitor.anomaly.enabled:true}") boolean anomalyEnabled,
      @Value("${monitor.anomaly.async-timeout-seconds:5}") long asyncTimeoutSeconds,
      @Value("${monitor.anomaly.fallback-to-threshold:true}") boolean fallbackToThreshold) {
    this.analyticsClient = analyticsClient;
    this.anomalyEnabled = anomalyEnabled;
    this.asyncTimeout = Duration.ofSeconds(asyncTimeoutSeconds);
    this.fallbackToThreshold = fallbackToThreshold;
  }

  public DataQualityResult checkRate(RateSnapshot current, RateSnapshot previous) {
    if (current == null) {
      return DataQualityResult.MISSING;
    }

    if (previous != null) {
      double changeBps = Math.abs(current.value() - previous.value()) * BPS_MULTIPLIER;
      if (changeBps > OUTLIER_BPS_THRESHOLD) {
        return DataQualityResult.OUTLIER;
      }
    }

    return DataQualityResult.VALID;
  }

  public boolean isStale(Instant lastUpdate, Duration maxAge) {
    return lastUpdate != null && Instant
        .now()
        .isAfter(lastUpdate.plus(maxAge));
  }

  public DataQualityResult checkBatch(List<RateSnapshot> snapshots) {
    if (snapshots == null || snapshots.isEmpty()) {
      return DataQualityResult.MISSING;
    }

    for (int i = 1; i < snapshots.size(); i++) {
      double changeBps = Math.abs(snapshots
          .get(i)
          .value() - snapshots
          .get(i - 1)
          .value()) * BPS_MULTIPLIER;
      if (changeBps > OUTLIER_BPS_THRESHOLD) {
        return DataQualityResult.OUTLIER;
      }
    }

    return DataQualityResult.VALID;
  }

  /**
   * Non-blocking anomaly-augmented quality check. Sends the current rate value to the analytics
   * worker's autoencoder endpoint on a virtual thread with a bounded timeout. If the worker flags
   * the value as anomalous the result is {@link DataQualityResult#SUSPECT_ANOMALY}; otherwise the
   * standard threshold result applies. On any worker error or timeout the check degrades to the
   * threshold-based result and logs {@code ANOMALY_WORKER_FALLBACK} once per unreachable transition
   * so the ingestion pipeline is never blocked.
   *
   * <p>Callers that need the result call {@code .join()} themselves; the pipeline itself may treat
   * the returned future as fire-and-forget and apply the {@code is_suspect_anomaly} flag when it
   * completes.</p>
   */
  public CompletableFuture<DataQualityResult> checkRateWithAnomaly(
      RateSnapshot current, RateSnapshot previous) {
    if (!anomalyEnabled || analyticsClient == null) {
      return CompletableFuture.completedFuture(fallbackResult(current, previous));
    }

    Map<String, Object> payload = Map.of("data", List.of(List.of(current.value())));
    CompletableFuture<DataQualityResult> detection = CompletableFuture
        .supplyAsync(() -> queryAnomalyWorker(payload))
        .orTimeout(asyncTimeout.toSeconds(), TimeUnit.SECONDS)
        .thenApply(mask -> {
          workerReachable.set(true);
          return mask != null && mask
              ? DataQualityResult.SUSPECT_ANOMALY
              : checkRate(current, previous);
        });

    return detection.exceptionally(ex -> {
      if (transitionToUnreachable()) {
        log.warn("ANOMALY_WORKER_FALLBACK: analytics worker unreachable ({}); using threshold checks",
            ex.getMessage());
      }
      return fallbackResult(current, previous);
    });
  }

  @SuppressWarnings("unchecked")
  private Boolean queryAnomalyWorker(Map<String, Object> payload) {
    Map<String, Object> result = analyticsClient.sendAnalysisRequest(ANOMALY_ENDPOINT, payload);
    if (result == null || result.containsKey("error")) {
      throw new IllegalStateException("anomaly worker returned an error response");
    }
    List<Boolean> anomalyMask = (List<Boolean>) result.get("anomaly_mask");
    if (anomalyMask == null || anomalyMask.isEmpty()) {
      return false;
    }
    return anomalyMask.get(0);
  }

  private DataQualityResult fallbackResult(RateSnapshot current, RateSnapshot previous) {
    return fallbackToThreshold ? checkRate(current, previous) : DataQualityResult.MISSING;
  }

  private boolean transitionToUnreachable() {
    return workerReachable.compareAndSet(true, false);
  }

  public enum DataQualityResult {
    VALID,
    MISSING,
    OUTLIER,
    STALE,
    SUSPECT_ANOMALY
  }
}
