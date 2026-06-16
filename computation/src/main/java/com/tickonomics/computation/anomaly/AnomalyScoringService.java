package com.tickonomics.computation.anomaly;

import com.tickonomics.computation.model.ModelArtifactService;
import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import com.tickonomics.persistence.entity.IliHistory;
import com.tickonomics.persistence.entity.ModelArtifact;
import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.repository.IliHistoryRepository;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnomalyScoringService {

  private static final Logger log = LoggerFactory.getLogger(AnomalyScoringService.class);
  static final String MODEL_TYPE = "autoencoder";
  static final int MIN_SAMPLES = 20;
  static final double MIN_THRESHOLD = 1e-6;

  private final IliHistoryRepository iliRepository;
  private final RateSnapshotRepository rateRepository;
  private final ModelArtifactService modelArtifactService;
  private final AnalyticsWorkerClient analyticsClient;

  private final int scoringWindow;

  public AnomalyScoringService(
      IliHistoryRepository iliRepository,
      RateSnapshotRepository rateRepository,
      ModelArtifactService modelArtifactService,
      AnalyticsWorkerClient analyticsClient,
      @Value("${monitor.anomaly.scoring-window:100}") int scoringWindow) {
    this.iliRepository = iliRepository;
    this.rateRepository = rateRepository;
    this.modelArtifactService = modelArtifactService;
    this.analyticsClient = analyticsClient;
    this.scoringWindow = scoringWindow;
  }

  public void scoreIliAnomalies() {
    List<IliHistory> rows = iliRepository.findLatestN(scoringWindow);
    if (rows.size() < MIN_SAMPLES) {
      log.info("Skipping ILI anomaly scoring: only {} rows (minimum {})", rows.size(), MIN_SAMPLES);
      return;
    }

    List<List<Double>> features = rows.stream()
        .map(r -> List.of(r.iliValue(), r.zRrp(), r.zSpread(), r.zVol()))
        .toList();

    String trainingData = features.toString();
    Map<String, Object> result;

    Optional<ModelArtifact> cached = modelArtifactService.findCachedModel(MODEL_TYPE, trainingData);
    if (cached.isPresent()) {
      result = detectWithExistingModel(cached.get(), features);
    } else {
      result = trainAndDetect(features);
    }

    if (result == null || result.containsKey("error")) {
      log.warn("Anomaly detection API returned error: {}", result);
      return;
    }

    writeIliScores(rows, result);
  }

  public void scoreRateAnomalies(String rateType) {
    List<RateSnapshot> rows = rateRepository.findLatestN(scoringWindow);
    if (rows.size() < MIN_SAMPLES) {
      log.info("Skipping rate anomaly scoring for {}: only {} rows (minimum {})",
          rateType, rows.size(), MIN_SAMPLES);
      return;
    }

    List<RateSnapshot> filtered = rows.stream()
        .filter(r -> r.rateType().equals(rateType))
        .toList();

    if (filtered.size() < MIN_SAMPLES) {
      log.info("Skipping rate anomaly scoring for {}: only {} filtered rows (minimum {})",
          rateType, filtered.size(), MIN_SAMPLES);
      return;
    }

    List<List<Double>> features = filtered.stream()
        .map(r -> List.of(r.value()))
        .toList();

    String trainingData = features.toString();
    Map<String, Object> result;

    Optional<ModelArtifact> cached = modelArtifactService.findCachedModel(MODEL_TYPE, trainingData);
    if (cached.isPresent()) {
      result = detectWithExistingModel(cached.get(), features);
    } else {
      result = trainAndDetect(features);
    }

    if (result == null || result.containsKey("error")) {
      log.warn("Anomaly detection API returned error for {}: {}", rateType, result);
      return;
    }

    writeRateScores(filtered, result);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> detectWithExistingModel(ModelArtifact artifact, List<List<Double>> features) {
    log.info("Using cached autoencoder model id={} for detection", artifact.id());

    Map<String, Object> payload = new HashMap<>();
    payload.put("data", features);

    if (artifact.stateData() != null) {
      payload.put("model_state", artifact.stateData());
    }
    if (artifact.trainingStats() != null) {
      payload.put("threshold", artifact.trainingStats());
    }

    return analyticsClient.sendAnalysisRequest("/api/v1/anomaly/detect", payload);
  }

  private Map<String, Object> trainAndDetect(List<List<Double>> features) {
    log.info("Training new autoencoder model on {} samples", features.size());

    Map<String, Object> payload = new HashMap<>();
    payload.put("data", features);

    Map<String, Object> trainResult = analyticsClient.sendAnalysisRequest(
        "/api/v1/anomaly/train", payload);

    if (trainResult == null || trainResult.containsKey("error")) {
      return trainResult;
    }

    String trainingData = features.toString();
    Object modelStateObj = trainResult.get("model_state");
    byte[] stateData = modelStateObj != null ? modelStateObj.toString().getBytes() : null;

    modelArtifactService.persistModel(
        MODEL_TYPE,
        "1.0",
        "{\"encoding_dim\":\"auto\"}",
        stateData,
        trainResult.toString(),
        features.size(),
        trainingData,
        null);

    return trainResult;
  }

  @SuppressWarnings("unchecked")
  private void writeIliScores(List<IliHistory> rows, Map<String, Object> result) {
    Object thresholdObj = result.get("threshold");
    double threshold = thresholdObj instanceof Number ? ((Number) thresholdObj).doubleValue() : 0.0;
    if (threshold < MIN_THRESHOLD) {
      log.warn("Anomaly threshold {} below minimum {}; skipping score write", threshold, MIN_THRESHOLD);
      return;
    }

    List<Boolean> anomalyMask = (List<Boolean>) result.get("anomaly_mask");
    List<Number> reconstructionErrors = (List<Number>) result.get("reconstruction_errors");

    if (anomalyMask == null || anomalyMask.size() != rows.size()) {
      log.warn("Anomaly mask size mismatch: mask={}, rows={}", anomalyMask != null ? anomalyMask.size() : 0, rows.size());
      return;
    }

    for (int i = 0; i < rows.size(); i++) {
      IliHistory row = rows.get(i);
      double score = reconstructionErrors != null && i < reconstructionErrors.size()
          ? reconstructionErrors.get(i).doubleValue()
          : 0.0;
      boolean isAnomaly = anomalyMask.get(i);

      iliRepository.updateAnomalyScore(row.time(), score, isAnomaly);
    }

    long anomalyCount = anomalyMask.stream().filter(Boolean::booleanValue).count();
    log.info("Wrote ILI anomaly scores: {} total, {} anomalies", rows.size(), anomalyCount);
  }

  @SuppressWarnings("unchecked")
  private void writeRateScores(List<RateSnapshot> rows, Map<String, Object> result) {
    Object thresholdObj = result.get("threshold");
    double threshold = thresholdObj instanceof Number ? ((Number) thresholdObj).doubleValue() : 0.0;
    if (threshold < MIN_THRESHOLD) {
      log.warn("Anomaly threshold {} below minimum {}; skipping score write", threshold, MIN_THRESHOLD);
      return;
    }

    List<Boolean> anomalyMask = (List<Boolean>) result.get("anomaly_mask");
    List<Number> reconstructionErrors = (List<Number>) result.get("reconstruction_errors");

    if (anomalyMask == null || anomalyMask.size() != rows.size()) {
      log.warn("Anomaly mask size mismatch: mask={}, rows={}", anomalyMask != null ? anomalyMask.size() : 0, rows.size());
      return;
    }

    for (int i = 0; i < rows.size(); i++) {
      RateSnapshot row = rows.get(i);
      double score = reconstructionErrors != null && i < reconstructionErrors.size()
          ? reconstructionErrors.get(i).doubleValue()
          : 0.0;
      boolean isAnomaly = anomalyMask.get(i);

      rateRepository.updateAnomalyScore(row.time(), row.rateType(), score, isAnomaly);
    }

    long anomalyCount = anomalyMask.stream().filter(Boolean::booleanValue).count();
    log.info("Wrote rate anomaly scores: {} total, {} anomalies", rows.size(), anomalyCount);
  }
}
