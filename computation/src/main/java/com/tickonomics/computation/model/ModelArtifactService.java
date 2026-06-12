package com.tickonomics.computation.model;

import com.tickonomics.persistence.entity.ModelArtifact;
import com.tickonomics.persistence.repository.ModelArtifactRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ModelArtifactService {

  private static final Logger log = LoggerFactory.getLogger(ModelArtifactService.class);
  private static final int KEEP_LATEST_VERSIONS = 3;

  private final ModelArtifactRepository repository;

  public ModelArtifactService(ModelArtifactRepository repository) {
    this.repository = repository;
  }

  public Optional<ModelArtifact> findCachedModel(String modelType, String trainingData) {
    String dataHash = computeSha256(trainingData);
    Optional<ModelArtifact> cached = repository.findByModelTypeAndDataHash(modelType, dataHash);
    cached.ifPresent(a -> log.info("Cache hit for model_type={}, data_hash={}", modelType, dataHash));
    return cached;
  }

  public Optional<ModelArtifact> findActiveModel(String modelType) {
    return repository.findActiveByModelType(modelType);
  }

  public long persistModel(String modelType,
                           String modelVersion,
                           String parameters,
                           byte[] stateData,
                           String trainingStats,
                           int trainedRows,
                           String trainingData,
                           String gitSha) {
    String dataHash = computeSha256(trainingData);

    ModelArtifact artifact = new ModelArtifact(
        null,
        modelType,
        modelVersion,
        parameters,
        stateData,
        trainingStats,
        Instant.now(),
        trainedRows,
        dataHash,
        gitSha,
        true);

    long id = repository.save(artifact);
    log.info("Persisted model_artifact id={}, model_type={}, data_hash={}", id, modelType, dataHash);

    repository.deactivateOlderVersions(modelType, KEEP_LATEST_VERSIONS);
    return id;
  }

  public void invalidateByTtl(String modelType, String ttlInterval) {
    log.info("Invalidating model_artifacts older than {} for model_type={}", ttlInterval, modelType);
    repository.deactivateByTtl(modelType, ttlInterval);
  }

  String computeSha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
