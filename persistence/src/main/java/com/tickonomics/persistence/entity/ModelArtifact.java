package com.tickonomics.persistence.entity;

import java.time.Instant;

public record ModelArtifact(
    Long id,
    String modelType,
    String modelVersion,
    String parameters,
    byte[] stateData,
    String trainingStats,
    Instant trainedAt,
    Integer trainedRows,
    String dataHash,
    String gitSha,
    boolean isActive) {

  public ModelArtifact {
    if (modelType == null || modelType.isBlank()) {
      throw new IllegalArgumentException("modelType must not be blank");
    }
    if (parameters == null || parameters.isBlank()) {
      throw new IllegalArgumentException("parameters must not be blank");
    }
  }
}
