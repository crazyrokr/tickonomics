package com.tickonomics.persistence.entity;

import java.time.Instant;

public record BacktestResultRecord(
    Long id,
    Instant runAt,
    String strategyConfig,
    String dateRange,
    Double sharpeRatio,
    Double maxDrawdown,
    Double winRate,
    Double profitFactor,
    String equityCurve,
    String gitSha,
    String datasetHash,
    String modelHyperparams,
    Integer rdsScore,
    String parameterSliceMetadata,
    String adjustedPValues) {

  public BacktestResultRecord {
    if (runAt == null) {
      throw new IllegalArgumentException("runAt must not be null");
    }
    if (strategyConfig == null || strategyConfig.isBlank()) {
      throw new IllegalArgumentException("strategyConfig must not be blank");
    }
    if (dateRange == null || dateRange.isBlank()) {
      throw new IllegalArgumentException("dateRange must not be blank");
    }
  }
}
