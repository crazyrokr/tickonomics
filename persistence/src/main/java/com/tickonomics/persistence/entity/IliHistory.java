package com.tickonomics.persistence.entity;

import java.time.Instant;

public record IliHistory(
    Instant time,
    double iliValue,
    double zRrp,
    double zSpread,
    double zVol,
    String dataStatus,
    String activeWeights,
    String proxyDivergenceStatus,
    Double proxyDivergenceScore,
    Double anomalyScore,
    Boolean isSuspectAnomaly) {
  public IliHistory {
    if (time == null) {
      throw new NullPointerException("time must not be null");
    }
    if (dataStatus == null || dataStatus.isBlank()) {
      throw new IllegalArgumentException("dataStatus must not be blank");
    }
  }
}
