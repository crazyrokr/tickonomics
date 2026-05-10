package com.tickonomics.computation.backtest;

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReproducibilityService {

  private final RestClientAnalyticsWorkerClient analyticsClient;
  private final String gitSha;

  public ReproducibilityService(
      RestClientAnalyticsWorkerClient analyticsClient,
      @Value("${tickonomics.git-sha:unknown}") String gitSha) {
    this.analyticsClient = analyticsClient;
    this.gitSha = gitSha;
  }

  public ReproducibilityContext captureContext(
      String modelName,
      String strategyConfig,
      Map<String, Object> hyperparams) {

    String datasetHash = computeDatasetHash(strategyConfig);
    int rdsScore = computeRdsScore(modelName, hyperparams);
    String hyperparamsJson = serializeHyperparams(hyperparams);

    return new ReproducibilityContext(
        gitSha,
        datasetHash,
        hyperparamsJson,
        rdsScore,
        null);
  }

  private String computeDatasetHash(String data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      return "unavailable";
    }
  }

  @SuppressWarnings("unchecked")
  private int computeRdsScore(String modelName, Map<String, Object> hyperparams) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model_name", modelName);
    payload.put("hyperparams", hyperparams);

    Map<String, Object> response = analyticsClient.sendAnalysisRequest(
        "/api/v1/reproducibility/score", payload);

    if (response.containsKey("error")) {
      return 0;
    }
    return response.get("rds_score") instanceof Number
        ? ((Number) response.get("rds_score")).intValue()
        : 0;
  }

  private String serializeHyperparams(Map<String, Object> hyperparams) {
    if (hyperparams == null || hyperparams.isEmpty()) {
      return "{}";
    }
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : hyperparams.entrySet()) {
      if (!first) sb.append(",");
      sb.append("\"").append(entry.getKey()).append("\":");
      if (entry.getValue() instanceof String) {
        sb.append("\"").append(entry.getValue()).append("\"");
      } else {
        sb.append(entry.getValue());
      }
      first = false;
    }
    sb.append("}");
    return sb.toString();
  }

  public record ReproducibilityContext(
      String gitSha,
      String datasetHash,
      String modelHyperparams,
      int rdsScore,
      String parameterSliceMetadata) {}
}
